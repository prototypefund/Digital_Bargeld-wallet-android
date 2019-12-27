package net.taler.wallet.history

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.taler.wallet.history.ReserveType.MANUAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HistoryEventTest {

    private val mapper = ObjectMapper().registerModule(KotlinModule())

    private val timestamp = Random.nextLong()
    private val exchangeBaseUrl = "https://exchange.test.taler.net/"

    @Test
    fun `test ExchangeAddedEvent`() {
        val builtIn = Random.nextBoolean()
        val json = """{
            "type": "exchange-added",
            "builtIn": $builtIn,
            "eventId": "exchange-added;https%3A%2F%2Fexchange.test.taler.net%2F",
            "exchangeBaseUrl": "https:\/\/exchange.test.taler.net\/",
            "timestamp": {
                "t_ms": $timestamp
            }
        }""".trimIndent()
        val event: ExchangeAddedEvent = mapper.readValue(json)

        assertEquals(builtIn, event.builtIn)
        assertEquals(exchangeBaseUrl, event.exchangeBaseUrl)
        assertEquals(timestamp, event.timestamp.ms)
    }

    @Test
    fun `test ExchangeUpdatedEvent`() {
        val json = """{
            "type": "exchange-updated",
            "eventId": "exchange-updated;https%3A%2F%2Fexchange.test.taler.net%2F",
            "exchangeBaseUrl": "https:\/\/exchange.test.taler.net\/",
            "timestamp": {
                "t_ms": $timestamp
            }
        }""".trimIndent()
        val event: ExchangeUpdatedEvent = mapper.readValue(json)

        assertEquals(exchangeBaseUrl, event.exchangeBaseUrl)
        assertEquals(timestamp, event.timestamp.ms)
    }

    @Test
    fun `test ReserveShortInfo`() {
        val json = """{
            "exchangeBaseUrl": "https:\/\/exchange.test.taler.net\/",
            "reserveCreationDetail": {
                "type": "manual"
            },
            "reservePub": "BRT2P0YMQSD5F48V9XHVNH73ZTS6EZC0KCQCPGPZQWTSQB77615G"
        }""".trimIndent()
        val info: ReserveShortInfo = mapper.readValue(json)

        assertEquals(exchangeBaseUrl, info.exchangeBaseUrl)
        assertEquals(MANUAL, info.reserveCreationDetail.type)
        assertEquals("BRT2P0YMQSD5F48V9XHVNH73ZTS6EZC0KCQCPGPZQWTSQB77615G", info.reservePub)
    }

    @Test
    fun `test ReserveBalanceUpdatedEvent`() {
        val json = """{
            "type": "reserve-balance-updated",
            "eventId": "reserve-balance-updated;K0H10Q6HB9WH0CKHQQMNH5C6GA7A9AR1E2XSS9G1KG3ZXMBVT26G",
            "amountExpected": "TESTKUDOS:23",
            "amountReserveBalance": "TESTKUDOS:10",
            "timestamp": {
                "t_ms": $timestamp
            },
            "newHistoryTransactions": [
                {
                    "amount": "TESTKUDOS:10",
                    "sender_account_url": "payto:\/\/x-taler-bank\/bank.test.taler.net\/894",
                    "timestamp": {
                        "t_ms": $timestamp
                    },
                    "wire_reference": "00000000004TR",
                    "type": "DEPOSIT"
                }
            ],
            "reserveShortInfo": {
                "exchangeBaseUrl": "https:\/\/exchange.test.taler.net\/",
                "reserveCreationDetail": {
                    "type": "manual"
                },
                "reservePub": "BRT2P0YMQSD5F48V9XHVNH73ZTS6EZC0KCQCPGPZQWTSQB77615G"
            }
        }""".trimIndent()
        val event: ReserveBalanceUpdatedEvent = mapper.readValue(json)

        assertEquals(timestamp, event.timestamp.ms)
        assertEquals("TESTKUDOS:23", event.amountExpected)
        assertEquals("TESTKUDOS:10", event.amountReserveBalance)
        assertEquals(1, event.newHistoryTransactions.size)
        assertTrue(event.newHistoryTransactions[0] is ReserveDepositTransaction)
        assertEquals(exchangeBaseUrl, event.reserveShortInfo.exchangeBaseUrl)
    }

    @Test
    fun `test HistoryWithdrawnEvent`() {
        val json = """{
            "type": "withdrawn",
            "withdrawSessionId": "974FT7JDNR20EQKNR21G1HV9PB6T5AZHYHX9NHR51Q30ZK3T10S0",
            "eventId": "withdrawn;974FT7JDNR20EQKNR21G1HV9PB6T5AZHYHX9NHR51Q30ZK3T10S0",
            "amountWithdrawnEffective": "TESTKUDOS:9.8",
            "amountWithdrawnRaw": "TESTKUDOS:10",
            "exchangeBaseUrl": "https:\/\/exchange.test.taler.net\/",
            "timestamp": {
                "t_ms": $timestamp
            },
            "withdrawalSource": {
                "type": "reserve",
                "reservePub": "BRT2P0YMQSD5F48V9XHVNH73ZTS6EZC0KCQCPGPZQWTSQB77615G"
            }
        }""".trimIndent()
        val event: HistoryWithdrawnEvent = mapper.readValue(json)

        assertEquals(
            "974FT7JDNR20EQKNR21G1HV9PB6T5AZHYHX9NHR51Q30ZK3T10S0",
            event.withdrawSessionId
        )
        assertEquals("TESTKUDOS:9.8", event.amountWithdrawnEffective)
        assertEquals("TESTKUDOS:10", event.amountWithdrawnRaw)
        assertTrue(event.withdrawalSource is WithdrawalSourceReserve)
        assertEquals(
            "BRT2P0YMQSD5F48V9XHVNH73ZTS6EZC0KCQCPGPZQWTSQB77615G",
            (event.withdrawalSource as WithdrawalSourceReserve).reservePub
        )
        assertEquals(exchangeBaseUrl, event.exchangeBaseUrl)
        assertEquals(timestamp, event.timestamp.ms)
    }

    @Test
    fun `test list of events as History`() {
        val builtIn = Random.nextBoolean()
        val json = """[
        {
            "type": "exchange-updated",
            "eventId": "exchange-updated;https%3A%2F%2Fexchange.test.taler.net%2F",
            "exchangeBaseUrl": "https:\/\/exchange.test.taler.net\/",
            "timestamp": {
                "t_ms": $timestamp
            }
        },
        {
            "type": "exchange-added",
            "builtIn": $builtIn,
            "eventId": "exchange-added;https%3A%2F%2Fexchange.test.taler.net%2F",
            "exchangeBaseUrl": "https:\/\/exchange.test.taler.net\/",
            "timestamp": {
                "t_ms": $timestamp
            }
        }
        ]""".trimIndent()
        val history: History = mapper.readValue(json)

        assertEquals(2, history.size)
        assertTrue(history[0] is ExchangeUpdatedEvent)
        assertTrue(history[1] is ExchangeAddedEvent)
    }

}
