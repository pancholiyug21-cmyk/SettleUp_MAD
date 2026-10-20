package com.example.splitwsie.domain.usecase

import com.example.splitwsie.domain.model.Debt
import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.ExpenseStatus
import com.example.splitwsie.domain.model.Settlement
import com.example.splitwsie.domain.util.Money
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class BalanceCalculator @Inject constructor() {

    /**
     * Net position per user in paise. Positive = the group owes them, negative = they owe the group.
     *
     * Settlements are folded in here rather than in the UI: when A pays B, A has discharged part
     * of their debt, so A's balance rises and B's falls. Only verified settlements count —
     * an in-flight payment must not move a balance before it is confirmed.
     */
    fun netBalancesInPaise(
        expenses: List<Expense>,
        settlements: List<Settlement> = emptyList()
    ): Map<String, Long> {
        val net = mutableMapOf<String, Long>()

        for (expense in expenses) {
            if (expense.status == ExpenseStatus.SETTLED) continue

            val totalPaise = Money.toPaise(expense.amount)
            net[expense.paidById] = (net[expense.paidById] ?: 0L) + totalPaise

            for (split in expense.splits) {
                val owedPaise = Money.toPaise(split.amountOwed)
                net[split.userId] = (net[split.userId] ?: 0L) - owedPaise
            }
        }

        for (settlement in settlements) {
            if (!settlement.countsTowardsBalance) continue
            val paid = Money.toPaise(settlement.amount)
            net[settlement.fromUserId] = (net[settlement.fromUserId] ?: 0L) + paid
            net[settlement.toUserId] = (net[settlement.toUserId] ?: 0L) - paid
        }

        return net
    }

    /**
     * Calculates the minimum set of transactions needed to settle all debts.
     *
     * Greedy largest-debtor-to-largest-creditor matching, which produces at most n-1 transfers.
     */
    fun calculateBalances(
        expenses: List<Expense>,
        settlements: List<Settlement> = emptyList()
    ): List<Debt> {
        val netBalances = netBalancesInPaise(expenses, settlements)

        val debtors = mutableListOf<Pair<String, Long>>()
        val creditors = mutableListOf<Pair<String, Long>>()

        // Working in whole paise means "settled" is exactly zero — no epsilon fudging needed.
        for ((userId, balance) in netBalances) {
            when {
                balance < 0L -> debtors.add(userId to -balance)
                balance > 0L -> creditors.add(userId to balance)
            }
        }

        // Deterministic ordering: amount first, then user id, so the same inputs always
        // produce the same settlement plan across launches and devices.
        debtors.sortWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
        creditors.sortWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })

        val optimizedDebts = mutableListOf<Debt>()
        var i = 0
        var j = 0

        while (i < debtors.size && j < creditors.size) {
            val (debtorId, debtorAmount) = debtors[i]
            val (creditorId, creditorAmount) = creditors[j]

            val settleAmount = min(debtorAmount, creditorAmount)
            if (settleAmount <= 0L) break

            optimizedDebts.add(
                Debt(
                    fromUserId = debtorId,
                    toUserId = creditorId,
                    amount = Money.fromPaise(settleAmount)
                )
            )

            val remainingDebtor = debtorAmount - settleAmount
            val remainingCreditor = creditorAmount - settleAmount

            if (remainingDebtor == 0L) i++ else debtors[i] = debtorId to remainingDebtor
            if (remainingCreditor == 0L) j++ else creditors[j] = creditorId to remainingCreditor
        }

        return optimizedDebts
    }

    /**
     * Calculates the exact pairwise balances between users without greedy debt simplification.
     * This simply aggregates all directed debts for each pair of users.
     */
    fun calculatePairwiseBalances(
        expenses: List<Expense>,
        settlements: List<Settlement> = emptyList()
    ): List<Debt> {
        // Map of <Debtor, Map<Creditor, Amount>>
        val pairwiseNet = mutableMapOf<String, MutableMap<String, Long>>()

        fun addDebt(debtorId: String, creditorId: String, amount: Long) {
            if (debtorId == creditorId) return
            val debtorMap = pairwiseNet.getOrPut(debtorId) { mutableMapOf() }
            debtorMap[creditorId] = (debtorMap[creditorId] ?: 0L) + amount
        }

        for (expense in expenses) {
            if (expense.status == ExpenseStatus.SETTLED) continue
            
            val payerId = expense.paidById
            for (split in expense.splits) {
                if (split.userId != payerId) {
                    val owedPaise = Money.toPaise(split.amountOwed)
                    addDebt(split.userId, payerId, owedPaise)
                }
            }
        }

        for (settlement in settlements) {
            if (!settlement.countsTowardsBalance) continue
            val paid = Money.toPaise(settlement.amount)
            val payerId = settlement.fromUserId
            val payeeId = settlement.toUserId
            
            // Settlement means payerId paid payeeId. This offsets any debt payerId owes payeeId.
            // We model this as payeeId owing payerId (which nets out the debt).
            addDebt(payeeId, payerId, paid)
        }

        // Net out A -> B and B -> A
        val finalDebts = mutableListOf<Debt>()
        val processedPairs = mutableSetOf<Pair<String, String>>()

        for ((debtorId, creditors) in pairwiseNet) {
            for ((creditorId, amountToCreditor) in creditors) {
                val pair = if (debtorId < creditorId) debtorId to creditorId else creditorId to debtorId
                if (processedPairs.contains(pair)) continue
                processedPairs.add(pair)

                val amountAtoB = if (debtorId == pair.first) amountToCreditor else (pairwiseNet[pair.first]?.get(pair.second) ?: 0L)
                val amountBtoA = if (debtorId == pair.second) amountToCreditor else (pairwiseNet[pair.second]?.get(pair.first) ?: 0L)

                val net = amountAtoB - amountBtoA
                if (net > 0) {
                    finalDebts.add(Debt(fromUserId = pair.first, toUserId = pair.second, amount = Money.fromPaise(net)))
                } else if (net < 0) {
                    finalDebts.add(Debt(fromUserId = pair.second, toUserId = pair.first, amount = Money.fromPaise(-net)))
                }
            }
        }

        return finalDebts
    }

    /**
     * What [userId] currently owes [otherUserId] (positive) after settlements, or 0 if nothing.
     * Used to validate that a payment never exceeds the debt it claims to settle.
     */
    fun outstandingBetween(
        expenses: List<Expense>,
        settlements: List<Settlement>,
        userId: String,
        otherUserId: String
    ): Double {
        val debt = calculateBalances(expenses, settlements)
            .firstOrNull { it.fromUserId == userId && it.toUserId == otherUserId }
        return debt?.amount ?: 0.0
    }

    /** Net position of a single user: positive = owed to them, negative = they owe. */
    fun netBalanceFor(
        expenses: List<Expense>,
        settlements: List<Settlement>,
        userId: String
    ): Double = Money.fromPaise(netBalancesInPaise(expenses, settlements)[userId] ?: 0L)
}
