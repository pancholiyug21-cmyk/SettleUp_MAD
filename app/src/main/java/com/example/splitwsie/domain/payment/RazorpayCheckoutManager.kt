package com.example.splitwsie.domain.payment

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class RazorpayOrderResult(
    val success: Boolean,
    val orderId: String? = null,
    val paymentId: String? = null,
    val amount: Long? = null,
    val currency: String? = null,
    val keyId: String? = null,
    val errorMessage: String? = null
)

data class RazorpayVerificationResult(
    val success: Boolean,
    val errorMessage: String? = null
)

@Singleton
class RazorpayCheckoutManager @Inject constructor(
    private val functions: FirebaseFunctions
) {
    suspend fun createPaymentOrder(
        amountPaise: Long,
        settlementId: String,
        receiverId: String,
        groupId: String?
    ): RazorpayOrderResult {
        return try {
            val requestData = hashMapOf(
                "amount" to amountPaise,
                "receipt" to settlementId,
                "receiverId" to receiverId,
                "groupId" to groupId
            )

            val result = functions
                .getHttpsCallable("createPaymentOrder")
                .call(requestData)
                .await()

            val responseData = result.getData() as? Map<*, *>
            if (responseData != null && responseData["success"] == true) {
                RazorpayOrderResult(
                    success = true,
                    orderId = responseData["orderId"] as? String,
                    paymentId = responseData["paymentId"] as? String,
                    amount = (responseData["amount"] as? Number)?.toLong(),
                    currency = responseData["currency"] as? String,
                    keyId = responseData["keyId"] as? String
                )
            } else {
                RazorpayOrderResult(
                    success = false,
                    errorMessage = responseData?.get("message") as? String ?: "Failed to create order"
                )
            }
        } catch (e: Exception) {
            RazorpayOrderResult(
                success = false,
                errorMessage = e.message ?: "Network error creating order"
            )
        }
    }

    suspend fun verifyPayment(
        paymentId: String,
        razorpayPaymentId: String,
        razorpayOrderId: String,
        signature: String
    ): RazorpayVerificationResult {
        return try {
            val requestData = hashMapOf(
                "paymentId" to paymentId,
                "razorpayPaymentId" to razorpayPaymentId,
                "razorpayOrderId" to razorpayOrderId,
                "razorpaySignature" to signature
            )

            val result = functions
                .getHttpsCallable("verifyPayment")
                .call(requestData)
                .await()

            val responseData = result.getData() as? Map<*, *>
            if (responseData != null && responseData["success"] == true) {
                RazorpayVerificationResult(success = true)
            } else {
                RazorpayVerificationResult(
                    success = false,
                    errorMessage = responseData?.get("message") as? String ?: "Verification failed"
                )
            }
        } catch (e: Exception) {
            RazorpayVerificationResult(
                success = false,
                errorMessage = e.message ?: "Network error verifying payment"
            )
        }
    }
}
