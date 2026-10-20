package com.example.splitwsie.presentation.payment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject

class RazorpayCheckoutActivity : ComponentActivity(), PaymentResultWithDataListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Checkout.preload(applicationContext)
        val checkout = Checkout()

        val keyId = intent.getStringExtra(EXTRA_KEY_ID)
        val orderId = intent.getStringExtra(EXTRA_ORDER_ID)
        val amount = intent.getLongExtra(EXTRA_AMOUNT, 0L)
        val currency = intent.getStringExtra(EXTRA_CURRENCY) ?: "INR"

        if (keyId == null || orderId == null || amount == 0L) {
            finishWithError("Missing required payment parameters")
            return
        }

        checkout.setKeyID(keyId)

        try {
            val options = JSONObject()
            options.put("name", "SETTLE Phase 3")
            options.put("description", "Secure Payment")
            options.put("currency", currency)
            options.put("amount", amount)
            options.put("order_id", orderId)

            val theme = JSONObject()
            theme.put("color", "#7447e1")
            options.put("theme", theme)

            checkout.open(this, options)
        } catch (e: Exception) {
            finishWithError(e.message ?: "Error launching checkout")
        }
    }

    override fun onPaymentSuccess(paymentId: String?, paymentData: PaymentData?) {
        val intent = Intent().apply {
            putExtra(RESULT_SUCCESS, true)
            putExtra(RESULT_PAYMENT_ID, paymentId)
            putExtra(RESULT_SIGNATURE, paymentData?.signature)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        val intent = Intent().apply {
            putExtra(RESULT_SUCCESS, false)
            putExtra(RESULT_ERROR_MSG, response)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun finishWithError(msg: String) {
        val intent = Intent().apply {
            putExtra(RESULT_SUCCESS, false)
            putExtra(RESULT_ERROR_MSG, msg)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    companion object {
        const val EXTRA_KEY_ID = "keyId"
        const val EXTRA_ORDER_ID = "orderId"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_CURRENCY = "currency"

        const val RESULT_SUCCESS = "success"
        const val RESULT_PAYMENT_ID = "paymentId"
        const val RESULT_SIGNATURE = "signature"
        const val RESULT_ERROR_MSG = "errorMsg"
    }
}
