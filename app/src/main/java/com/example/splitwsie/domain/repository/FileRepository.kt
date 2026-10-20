package com.example.splitwsie.domain.repository

interface FileRepository {
    /**
     * Uploads an expense receipt image to Firebase Storage and returns the download URL.
     */
    suspend fun uploadExpenseReceipt(groupId: String?, expenseId: String, uriString: String): Result<String>

    /**
     * Deletes a receipt image from Firebase Storage using its download URL.
     */
    suspend fun deleteExpenseReceipt(receiptUrl: String): Result<Unit>
}
