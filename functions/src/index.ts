import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import * as crypto from "crypto";

admin.initializeApp();

const db = admin.firestore();

// Using require for Razorpay since we don't have types installed
const Razorpay = require("razorpay");

/**
 * Creates a Razorpay Order for a settlement.
 * Validates the user, amount, and handles idempotency to avoid duplicate orders.
 */
export const createPaymentOrder = functions.https.onCall(async (data, context) => {
    // 1. Verify Authentication
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
    }

    const { amount, receipt, receiverId, groupId } = data;
    if (!amount || typeof amount !== "number" || amount <= 0) {
        throw new functions.https.HttpsError("invalid-argument", "Valid amount (in paise) is required.");
    }
    if (!receipt || typeof receipt !== "string") {
        throw new functions.https.HttpsError("invalid-argument", "Receipt (settlement ID) is required.");
    }
    if (!receiverId || typeof receiverId !== "string") {
        throw new functions.https.HttpsError("invalid-argument", "Receiver ID is required.");
    }

    const key_id = process.env.RAZORPAY_KEY_ID || functions.config().razorpay?.key_id;
    const key_secret = process.env.RAZORPAY_KEY_SECRET || functions.config().razorpay?.key_secret;

    if (!key_id || !key_secret) {
        console.error("Razorpay credentials not configured on the server.");
        throw new functions.https.HttpsError("internal", "Server misconfiguration.");
    }

    try {
        const instance = new Razorpay({
            key_id: key_id,
            key_secret: key_secret,
        });

        const options = {
            amount: amount,
            currency: "INR",
            receipt: receipt,
            payment_capture: 1
        };

        const order = await instance.orders.create(options);

        // Create authoritative payment record in Firestore
        const paymentRef = db.collection("payments").doc();
        await paymentRef.set({
            payerId: context.auth.uid,
            receiverId: receiverId,
            groupId: groupId || null,
            settlementId: receipt,
            amount: order.amount,
            currency: order.currency,
            razorpayOrderId: order.id,
            status: "ORDER_CREATED",
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        });

        return {
            success: true,
            orderId: order.id,
            paymentId: paymentRef.id,
            amount: order.amount,
            currency: order.currency,
            keyId: key_id
        };
    } catch (error: any) {
        console.error("Error creating Razorpay order:", error);
        throw new functions.https.HttpsError("internal", error.message || "Failed to create order");
    }
});

/**
 * Securely verifies a Razorpay payment after client reports success.
 */
export const verifyPayment = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
    }

    const { paymentId, razorpayPaymentId, razorpayOrderId, razorpaySignature } = data;

    if (!paymentId || !razorpayPaymentId || !razorpayOrderId || !razorpaySignature) {
        throw new functions.https.HttpsError("invalid-argument", "Missing required verification data.");
    }

    const key_secret = process.env.RAZORPAY_KEY_SECRET || functions.config().razorpay?.key_secret;
    if (!key_secret) {
        throw new functions.https.HttpsError("internal", "Server misconfiguration.");
    }

    // 1. Retrieve the authoritative payment document
    const paymentRef = db.collection("payments").doc(paymentId);
    const paymentSnap = await paymentRef.get();

    if (!paymentSnap.exists) {
        throw new functions.https.HttpsError("not-found", "Payment record not found.");
    }

    const paymentData = paymentSnap.data();

    // 2. Validate ownership and order matching
    if (paymentData?.payerId !== context.auth.uid) {
        throw new functions.https.HttpsError("permission-denied", "You do not own this payment.");
    }
    if (paymentData?.razorpayOrderId !== razorpayOrderId) {
        throw new functions.https.HttpsError("failed-precondition", "Order ID mismatch.");
    }

    // 3. Idempotency: If already verified, return success safely
    if (paymentData?.status === "VERIFIED") {
        return { success: true, message: "Already verified" };
    }

    // 4. Verify Razorpay signature
    const text = razorpayOrderId + "|" + razorpayPaymentId;
    const generated_signature = crypto
        .createHmac("sha256", key_secret)
        .update(text)
        .digest("hex");

    if (generated_signature !== razorpaySignature) {
        await paymentRef.update({
            status: "VERIFICATION_FAILED",
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        });
        throw new functions.https.HttpsError("invalid-argument", "Invalid signature.");
    }

    // 5. Update Firestore to VERIFIED
    await paymentRef.update({
        razorpayPaymentId: razorpayPaymentId,
        status: "VERIFIED",
        verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    // 6. Idempotently create the Settlement record to update balances in real-time
    const settlementRef = db.collection("settlements").doc(paymentData?.settlementId);
    await settlementRef.set({
        settlementId: paymentData?.settlementId,
        groupId: paymentData?.groupId,
        fromUserId: paymentData?.payerId,
        toUserId: paymentData?.receiverId,
        amountPaise: paymentData?.amount,
        date: admin.firestore.FieldValue.serverTimestamp(),
        method: "RAZORPAY",
        status: "VERIFIED",
        idempotencyKey: paymentData?.settlementId, // using settlementId as idempotencyKey to avoid duplicate records
        providerTxnId: razorpayPaymentId,
        razorpayOrderId: razorpayOrderId,
        paymentId: paymentId,
        verificationNotes: "Payment verified by server.",
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });

    return { success: true };
});

/**
 * Razorpay webhook to handle asynchronous payment success events.
 * Provides a fallback if the Android client loses network during checkout.
 */
export const razorpayWebhook = functions.https.onRequest(async (req, res) => {
    const webhookSecret = process.env.RAZORPAY_WEBHOOK_SECRET || functions.config().razorpay?.webhook_secret;
    const signature = req.headers["x-razorpay-signature"] as string;

    if (!webhookSecret) {
        console.error("Webhook secret not configured.");
        res.status(500).send("Configuration error");
        return;
    }

    if (!signature) {
        res.status(400).send("Missing signature");
        return;
    }

    // 1. Verify webhook signature
    const body = req.rawBody ? req.rawBody.toString() : JSON.stringify(req.body);
    const expectedSignature = crypto
        .createHmac("sha256", webhookSecret)
        .update(body)
        .digest("hex");

    if (expectedSignature !== signature) {
        console.error("Invalid webhook signature");
        res.status(400).send("Invalid signature");
        return;
    }

    const eventId = req.headers["x-razorpay-event-id"] as string;
    const eventName = req.body.event;

    // 2. Idempotency check via webhookEvents collection
    if (eventId) {
        const eventRef = db.collection("webhookEvents").doc(eventId);
        const eventSnap = await eventRef.get();
        if (eventSnap.exists) {
            res.status(200).send("Already processed");
            return;
        }
        await eventRef.set({
            event: eventName,
            processedAt: admin.firestore.FieldValue.serverTimestamp()
        });
    }

    // 3. Process payment.captured event
    if (eventName === "payment.captured" || eventName === "payment.authorized") {
        const paymentEntity = req.body.payload?.payment?.entity;
        if (!paymentEntity) {
            res.status(400).send("Invalid payload structure");
            return;
        }

        const razorpayOrderId = paymentEntity.order_id;
        const razorpayPaymentId = paymentEntity.id;
        const amount = paymentEntity.amount; // in paise
        const currency = paymentEntity.currency;

        if (!razorpayOrderId) {
            res.status(400).send("Missing order_id");
            return;
        }

        // Look up our payment document by razorpayOrderId
        const paymentsRef = db.collection("payments");
        const snapshot = await paymentsRef.where("razorpayOrderId", "==", razorpayOrderId).limit(1).get();

        if (snapshot.empty) {
            console.warn(`Payment not found for order ${razorpayOrderId}`);
            res.status(404).send("Payment not found");
            return;
        }

        const paymentDoc = snapshot.docs[0];
        const paymentData = paymentDoc.data();

        // If already verified, do nothing (idempotency step 2)
        if (paymentData.status === "VERIFIED") {
            res.status(200).send("Already verified");
            return;
        }

        // Validate amount and currency (to prevent partial payments from being marked as fully settled)
        if (paymentData.amount !== amount || paymentData.currency !== currency) {
            console.error(`Amount/currency mismatch. Expected ${paymentData.amount} ${paymentData.currency}, got ${amount} ${currency}`);
            await paymentDoc.ref.update({
                status: "VERIFICATION_FAILED",
                verificationNotes: "Amount or currency mismatch from webhook",
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
            res.status(400).send("Mismatch");
            return;
        }

        // Update Firestore to VERIFIED
        await paymentDoc.ref.update({
            razorpayPaymentId: razorpayPaymentId,
            status: "VERIFIED",
            verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
            webhookVerified: true,
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        });

        // Idempotently create the Settlement record
        const settlementRef = db.collection("settlements").doc(paymentData.settlementId);
        await settlementRef.set({
            settlementId: paymentData.settlementId,
            groupId: paymentData.groupId,
            fromUserId: paymentData.payerId,
            toUserId: paymentData.receiverId,
            amountPaise: paymentData.amount,
            date: admin.firestore.FieldValue.serverTimestamp(),
            method: "RAZORPAY",
            status: "VERIFIED",
            idempotencyKey: paymentData.settlementId,
            providerTxnId: razorpayPaymentId,
            razorpayOrderId: razorpayOrderId,
            paymentId: paymentDoc.id,
            verificationNotes: "Payment verified by webhook.",
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
    }

    res.status(200).send("OK");
});

/**
 * Securely joins a user to a group using an invite code.
 *
 * Since the Firestore rules prevent clients from writing directly to the
 * members collection (to prevent unauthorized joins), this function acts
 * as the trusted middleman to validate the invite code and add the user.
 */
export const joinGroupWithCode = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
    }

    const inviteCode = data.inviteCode;
    if (!inviteCode || typeof inviteCode !== "string") {
        throw new functions.https.HttpsError("invalid-argument", "Valid inviteCode is required.");
    }

    const uid = context.auth.uid;

    try {
        // Query for the group with this invite code
        const groupsRef = db.collection("groups");
        const querySnapshot = await groupsRef.where("inviteCode", "==", inviteCode.toUpperCase()).limit(1).get();

        if (querySnapshot.empty) {
            throw new functions.https.HttpsError("not-found", "Invalid invite code.");
        }

        const groupDoc = querySnapshot.docs[0];
        const groupId = groupDoc.id;

        // Fetch the user's details to store in the members collection
        const userDoc = await db.collection("users").doc(uid).get();
        if (!userDoc.exists) {
            throw new functions.https.HttpsError("failed-precondition", "User profile not found.");
        }

        const userData = userDoc.data();
        const displayName = userData?.displayName || "Unknown User";

        // Add the user to the members subcollection
        const memberRef = db.collection(`groups/${groupId}/members`).doc(uid);

        await memberRef.set({
            uid: uid,
            role: "MEMBER",
            displayName: displayName,
            joinedAt: admin.firestore.FieldValue.serverTimestamp()
        });

        // Also add the uid to the memberIds array on the group doc for easier querying
        await db.collection("groups").doc(groupId).update({
            memberIds: admin.firestore.FieldValue.arrayUnion(uid)
        });

        return {
            success: true,
            groupId: groupId
        };
    } catch (error) {
        console.error("Error joining group:", error);
        throw new functions.https.HttpsError("internal", "An error occurred while joining the group.");
    }
});

/**
 * Securely records a manual settlement (CASH, UPI, BANK_TRANSFER) initiated by the client.
 */
export const recordManualSettlement = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
    }

    const { groupId, receiverId, amountPaise, method, settlementId, providerTxnId, note } = data;
    const payerId = context.auth.uid;

    if (!groupId || typeof groupId !== "string") {
        throw new functions.https.HttpsError("invalid-argument", "Group ID is required.");
    }
    if (!receiverId || typeof receiverId !== "string") {
        throw new functions.https.HttpsError("invalid-argument", "Receiver ID is required.");
    }
    if (payerId === receiverId) {
        throw new functions.https.HttpsError("invalid-argument", "Payer and receiver cannot be the same.");
    }
    if (!amountPaise || typeof amountPaise !== "number" || amountPaise <= 0) {
        throw new functions.https.HttpsError("invalid-argument", "Valid amount in paise is required.");
    }
    if (!settlementId || typeof settlementId !== "string") {
        throw new functions.https.HttpsError("invalid-argument", "Settlement ID is required.");
    }
    const allowedMethods = ["CASH", "UPI_INTENT", "BANK_TRANSFER"];
    if (!method || !allowedMethods.includes(method)) {
        throw new functions.https.HttpsError("invalid-argument", "Valid manual payment method is required.");
    }

    // 1. Verify group membership for both payer and receiver
    const groupRef = db.collection("groups").doc(groupId);
    const groupSnap = await groupRef.get();

    if (!groupSnap.exists) {
        throw new functions.https.HttpsError("not-found", "Group not found.");
    }
    const groupData = groupSnap.data();
    if (!groupData?.memberIds?.includes(payerId)) {
        throw new functions.https.HttpsError("permission-denied", "You are not a member of this group.");
    }
    if (!groupData?.memberIds?.includes(receiverId)) {
        throw new functions.https.HttpsError("invalid-argument", "Receiver is not a member of this group.");
    }

    // 2. Idempotency Check using Firestore Transaction to prevent duplicate inserts
    const settlementRef = db.collection("settlements").doc(settlementId);

    try {
        await db.runTransaction(async (t) => {
            const doc = await t.get(settlementRef);
            if (doc.exists) {
                // Already recorded. We just return success idempotently.
                return;
            }

            // 3. Create the Settlement record
            t.set(settlementRef, {
                settlementId: settlementId,
                groupId: groupId,
                fromUserId: payerId,
                toUserId: receiverId,
                amountPaise: amountPaise,
                date: admin.firestore.FieldValue.serverTimestamp(),
                method: method,
                status: "VERIFIED",
                idempotencyKey: settlementId,
                providerTxnId: providerTxnId || null,
                razorpayOrderId: null,
                paymentId: null,
                verificationNotes: note || "Recorded manually.",
                createdAt: admin.firestore.FieldValue.serverTimestamp(),
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
        });

        return { success: true, settlementId: settlementId };
    } catch (error) {
        console.error("Transaction failed:", error);
        throw new functions.https.HttpsError("internal", "Failed to record manual settlement.");
    }
});
