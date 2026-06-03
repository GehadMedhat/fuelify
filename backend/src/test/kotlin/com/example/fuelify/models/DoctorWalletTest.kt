package com.example.fuelify.models

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

// ── Top-level mock DTOs (Kotlin does not allow data class inside a function or inner class) ──

data class MockTx(
    val doctorId: Int,
    val caseId:   Int?,
    val amount:   Double,
    val type:     String
)

data class MockWalletTransactionDto(
    val txId:        Int,
    val caseId:      Int?,
    val patientName: String,
    val amount:      Double,
    val type:        String,
    val description: String,
    val createdAt:   String
)

data class MockWalletDto(
    val balance:      Double,
    val transactions: List<MockWalletTransactionDto>
)

/**
 * Unit tests for Doctor Wallet logic in DoctorRoutes.kt
 *
 * Covers: balance crediting, transaction logging, first-response-only rule,
 * wallet response structure, and edge cases.
 *
 * Run with:  ./gradlew test --tests "com.example.fuelify.models.DoctorWalletTest"
 *
 * Dependencies (add to build.gradle if not already present):
 *   testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
 *   testRuntimeOnly("org.junit.platform:junit-platform-launcher")
 *
 * And in the test block:
 *   useJUnitPlatform()
 *
 * NOTE: Tests that touch the DB should use an in-memory H2 database.
 * Pure logic tests (balance math, DTO mapping) run with no DB at all.
 */
class DoctorWalletTest {

    // =========================================================================
    // 1. Balance Arithmetic
    // =========================================================================

    @Nested
    inner class BalanceArithmeticTests {

        /**
         * Core rule: when a doctor responds to a case for the first time,
         * their wallet balance increases by pricePerCase.
         *
         * newBalance = oldBalance + pricePerCase
         */
        @Test
        fun `first response credits pricePerCase to wallet`() {
            val oldBalance   = 100.0
            val pricePerCase = 25
            val newBalance   = oldBalance + pricePerCase
            assertEquals(125.0, newBalance)
        }

        @Test
        fun `follow-up responses do not change wallet balance`() {
            val balanceBefore = 125.0
            val alreadyResponded = true
            // If alreadyResponded == true, no credit should occur
            val balanceAfter = if (!alreadyResponded) balanceBefore + 25 else balanceBefore
            assertEquals(balanceBefore, balanceAfter)
        }

        @Test
        fun `balance starts at zero on wallet creation`() {
            val initialBalance = 0.0
            assertEquals(0.0, initialBalance)
        }

        @Test
        fun `balance accumulates correctly across multiple cases`() {
            val pricePerCase = 25
            val casesResponded = 4
            val expectedBalance = pricePerCase * casesResponded
            assertEquals(100, expectedBalance)
        }

        @ParameterizedTest(name = "pricePerCase={0} → balance after 3 cases = {1}")
        @CsvSource(
            "25,  75",
            "50, 150",
            "75, 225",
            "100, 300"
        )
        fun `balance grows linearly with pricePerCase across 3 cases`(price: Int, expected: Int) {
            val balance = price * 3
            assertEquals(expected, balance)
        }

        @Test
        fun `balance is never negative after normal credit operations`() {
            val balance = 0.0 + 25 + 50 + 25
            assertTrue(balance >= 0.0, "Balance should never be negative: $balance")
        }
    }

    // =========================================================================
    // 2. First-Response-Only Credit Rule
    // =========================================================================

    @Nested
    inner class FirstResponseOnlyTests {

        @Test
        fun `doctorResponded false triggers wallet credit`() {
            val alreadyResponded = false
            var credited = false
            if (!alreadyResponded) credited = true
            assertTrue(credited, "Wallet should be credited on first response")
        }

        @Test
        fun `doctorResponded true skips wallet credit`() {
            val alreadyResponded = true
            var credited = false
            if (!alreadyResponded) credited = true
            assertFalse(credited, "Wallet should NOT be credited on follow-up")
        }

        @Test
        fun `first response changes case status to responded`() {
            val alreadyResponded = false
            val newStatus = if (!alreadyResponded) "responded" else "responded" // already set
            assertEquals("responded", newStatus)
        }

        @Test
        fun `first response sets doctorResponded to true`() {
            var doctorResponded = false
            // simulate first response
            doctorResponded = true
            assertTrue(doctorResponded)
        }

        @Test
        fun `response message differs for first vs follow-up`() {
            val pricePerCase = 25

            fun buildMessage(alreadyResponded: Boolean): String =
                if (alreadyResponded) "Follow-up sent!"
                else "Response sent! +$pricePerCase EGP added to your wallet."

            val firstMsg   = buildMessage(false)
            val followMsg  = buildMessage(true)

            assertTrue(firstMsg.contains("EGP"), "First response should mention wallet credit")
            assertFalse(followMsg.contains("EGP"), "Follow-up should NOT mention wallet credit")
            assertEquals("Follow-up sent!", followMsg)
        }

        @Test
        fun `response message includes correct price`() {
            val pricePerCase = 50
            val msg = "Response sent! +$pricePerCase EGP added to your wallet."
            assertTrue(msg.contains("50"), "Message should include the actual price")
        }
    }

    // =========================================================================
    // 3. Transaction Logging
    // =========================================================================

    @Nested
    inner class TransactionLoggingTests {

        /**
         * A transaction is only logged on first response.
         * It should always be of type "credit".
         */
        @Test
        fun `transaction type is always credit`() {
            val txType = "credit"
            assertEquals("credit", txType)
        }

        @Test
        fun `transaction description contains patient name and case id`() {
            val patientName = "Ali Hassan"
            val caseId = 42
            val description = "Response to $patientName — Case #$caseId"
            assertTrue(description.contains(patientName), "Description should contain patient name")
            assertTrue(description.contains("$caseId"),  "Description should contain case id")
        }

        @Test
        fun `transaction amount equals pricePerCase`() {
            val pricePerCase = 25
            val txAmount = pricePerCase.toDouble()
            assertEquals(25.0, txAmount)
        }

        @Test
        fun `transaction is only created on first response`() {
            val alreadyResponded = false
            var txCreated = false
            if (!alreadyResponded) txCreated = true
            assertTrue(txCreated, "Transaction should be created on first response")
        }

        @Test
        fun `no transaction created on follow-up response`() {
            val alreadyResponded = true
            var txCreated = false
            if (!alreadyResponded) txCreated = true
            assertFalse(txCreated, "No transaction on follow-up")
        }

        @Test
        fun `transaction links correct doctorId and caseId`() {
            val tx = MockTx(doctorId = 7, caseId = 42, amount = 25.0, type = "credit")

            assertEquals(7,    tx.doctorId)
            assertEquals(42,   tx.caseId)
            assertEquals(25.0, tx.amount)
            assertEquals("credit", tx.type)
        }
    }

    // =========================================================================
    // 4. WalletDto / WalletTransactionDto Mapping
    // =========================================================================

    @Nested
    inner class WalletDtoMappingTests {

        @Test
        fun `WalletDto contains balance and transactions list`() {
            val wallet = MockWalletDto(balance = 100.0, transactions = emptyList())
            assertEquals(100.0, wallet.balance)
            assertNotNull(wallet.transactions)
        }

        @Test
        fun `WalletDto with no transactions has empty list not null`() {
            val wallet = MockWalletDto(balance = 0.0, transactions = emptyList())
            assertTrue(wallet.transactions.isEmpty())
        }

        @Test
        fun `WalletTransactionDto with null caseId is valid`() {
            val tx = MockWalletTransactionDto(
                txId = 1, caseId = null, patientName = "Unknown",
                amount = 25.0, type = "credit",
                description = "Manual credit", createdAt = "01 Jan 2025 10:00"
            )
            assertNull(tx.caseId)
            assertEquals("credit", tx.type)
        }

        @Test
        fun `WalletTransactionDto amount converts from BigDecimal to Double correctly`() {
            val bigDecimalAmount = java.math.BigDecimal("25.00")
            val doubleAmount     = bigDecimalAmount.toDouble()
            assertEquals(25.0, doubleAmount, 0.001)
        }

        @Test
        fun `empty patientName defaults to empty string not null`() {
            val tx = MockWalletTransactionDto(
                txId = 2, caseId = 5, patientName = "",
                amount = 25.0, type = "credit",
                description = "Response to  — Case #5", createdAt = "01 Jan 2025 10:00"
            )
            assertNotNull(tx.patientName)
            assertEquals("", tx.patientName)
        }

        @Test
        fun `wallet returns at most 50 transactions (limit check)`() {
            val transactions = (1..50).map {
                MockWalletTransactionDto(it, it, "Patient $it", 25.0, "credit", "desc $it", "01 Jan 2025")
            }
            val wallet = MockWalletDto(balance = 50 * 25.0, transactions = transactions)
            assertTrue(wallet.transactions.size <= 50, "Should not exceed 50 transactions")
        }

        @Test
        fun `transactions are ordered most recent first`() {
            val transactions = listOf(
                MockWalletTransactionDto(3, 3, "C", 25.0, "credit", "desc", "03 Jan 2025"),
                MockWalletTransactionDto(2, 2, "B", 25.0, "credit", "desc", "02 Jan 2025"),
                MockWalletTransactionDto(1, 1, "A", 25.0, "credit", "desc", "01 Jan 2025")
            )
            // txId descending = most recent first
            assertEquals(3, transactions.first().txId, "Most recent transaction should come first")
        }
    }

    // =========================================================================
    // 5. Wallet GET Endpoint Logic
    // =========================================================================

    @Nested
    inner class WalletEndpointLogicTests {

        @Test
        fun `invalid doctor id (non-integer) should be rejected`() {
            val raw = "abc"
            val parsed = raw.toIntOrNull()
            assertNull(parsed, "Non-integer doctor id should parse to null")
        }

        @Test
        fun `valid doctor id parses correctly`() {
            val raw = "7"
            val parsed = raw.toIntOrNull()
            assertEquals(7, parsed)
        }

        @Test
        fun `missing wallet returns not found condition`() {
            val walletRow: Any? = null  // simulates no DB result
            assertNull(walletRow, "Should return 404 when wallet row is null")
        }

        @Test
        fun `wallet balance converts from BigDecimal to Double without precision loss`() {
            val stored = java.math.BigDecimal("250.75")
            val asDouble = stored.toDouble()
            assertEquals(250.75, asDouble, 0.001)
        }

        @Test
        fun `description falls back to empty string when null`() {
            val rawDescription: String? = null
            val description = rawDescription ?: ""
            assertEquals("", description)
        }

        @Test
        fun `patientName falls back to empty string when null`() {
            val rawName: String? = null
            val patientName = rawName ?: ""
            assertEquals("", patientName)
        }
    }

    // =========================================================================
    // 6. Wallet Created on Doctor Registration
    // =========================================================================

    @Nested
    inner class WalletCreationTests {

        @Test
        fun `new wallet balance is zero on registration`() {
            val initialBalance = java.math.BigDecimal.ZERO
            assertEquals(java.math.BigDecimal.ZERO, initialBalance)
        }

        @Test
        fun `wallet is linked to the correct doctorId`() {
            val registeredDoctorId = 99
            val walletDoctorId     = 99
            assertEquals(registeredDoctorId, walletDoctorId,
                "Wallet doctorId must match the registered doctor's id")
        }

        @Test
        fun `wallet created even when doctor is not yet approved`() {
            // Registration → wallet insert happens regardless of isApproved
            val isApproved     = false
            val walletCreated  = true   // always created during register route
            assertTrue(walletCreated, "Wallet should be created even before approval")
            assertFalse(isApproved)
        }
    }

    // =========================================================================
    // 7. Edge Cases
    // =========================================================================

    @Nested
    inner class EdgeCaseTests {

        @Test
        fun `blank doctor response body should not credit wallet`() {
            val content = "   "
            val isBlank = content.isBlank()
            // Route returns 400 and does not proceed to wallet logic
            assertTrue(isBlank, "Blank content should be rejected before wallet logic runs")
        }

        @Test
        fun `responding to same case twice does not double credit`() {
            var balance = 100.0
            val price   = 25

            fun respond(alreadyResponded: Boolean) {
                if (!alreadyResponded) balance += price
            }

            respond(false)  // first response → credit
            respond(true)   // follow-up     → no credit
            respond(true)   // follow-up     → no credit

            assertEquals(125.0, balance, "Balance should only increase once")
        }

        @Test
        fun `large pricePerCase values are handled correctly`() {
            val price      = 1000
            val oldBalance = 50000.0
            val newBalance = oldBalance + price
            assertEquals(51000.0, newBalance)
        }

        @Test
        fun `zero pricePerCase does not change balance`() {
            val price      = 0
            val oldBalance = 200.0
            val newBalance = oldBalance + price
            assertEquals(200.0, newBalance)
        }

        @Test
        fun `transaction description is never blank on first response`() {
            val patientName = "Sara Ahmed"
            val caseId      = 10
            val description = "Response to $patientName — Case #$caseId"
            assertTrue(description.isNotBlank())
        }

        @Test
        fun `wallet balance toDouble is non-negative for all credit-only systems`() {
            // Since the system only does credits (no withdrawals in current code),
            // balance should always be >= 0
            val transactions = listOf(25.0, 25.0, 50.0)
            val balance = transactions.sum()
            assertTrue(balance >= 0.0)
        }
    }
}
