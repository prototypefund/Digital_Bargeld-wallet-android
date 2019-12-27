package net.taler.wallet.history

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class ReserveTransactionTest {

    private val mapper = ObjectMapper().registerModule(KotlinModule())

    private val timestamp = Random.nextLong()

    @Test
    fun `test ExchangeAddedEvent`() {
        val senderAccountUrl = "payto://x-taler-bank/bank.test.taler.net/894"
        val json = """{
            "amount": "TESTKUDOS:10",
            "sender_account_url": "payto:\/\/x-taler-bank\/bank.test.taler.net\/894",
            "timestamp": {
                "t_ms": $timestamp
            },
            "wire_reference": "00000000004TR",
            "type": "DEPOSIT"
        }""".trimIndent()
        val transaction: ReserveDepositTransaction = mapper.readValue(json)

        assertEquals("TESTKUDOS:10", transaction.amount)
        assertEquals(senderAccountUrl, transaction.senderAccountUrl)
        assertEquals("00000000004TR", transaction.wireReference)
        assertEquals(timestamp, transaction.timestamp.ms)
    }

}
