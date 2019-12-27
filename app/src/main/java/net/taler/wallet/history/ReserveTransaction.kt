package net.taler.wallet.history

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME
import com.fasterxml.jackson.annotation.JsonTypeName


@JsonTypeInfo(
    use = NAME,
    include = PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ReserveDepositTransaction::class, name = "DEPOSIT")
)
abstract class ReserveTransaction


@JsonTypeName("DEPOSIT")
class ReserveDepositTransaction(
    /**
     * Amount withdrawn.
     */
    val amount: String,
    /**
     * Sender account payto://-URL
     */
    @JsonProperty("sender_account_url")
    val senderAccountUrl: String,
    /**
     * Transfer details uniquely identifying the transfer.
     */
    @JsonProperty("wire_reference")
    val wireReference: String,
    /**
     * Timestamp of the incoming wire transfer.
     */
    val timestamp: Timestamp
) : ReserveTransaction()
