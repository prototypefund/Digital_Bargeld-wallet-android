package net.taler.wallet.payment

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.taler.wallet.Amount


@JsonIgnoreProperties(ignoreUnknown = true)
data class ContractTerms(
    val summary: String,
    val products: List<ContractProduct>,
    val amount: Amount
)

interface Product {
    val id: String
    val description: String
    val price: Amount
    val location: String?
}

@JsonIgnoreProperties("totalPrice")
data class ContractProduct(
    @JsonProperty("product_id")
    override val id: String,
    override val description: String,
    override val price: Amount,
    @JsonProperty("delivery_location")
    override val location: String?,
    val quantity: Int
) : Product {

    val totalPrice: Amount by lazy {
        val amount = price.amount.toDouble() * quantity
        Amount(price.currency, amount.toString())
    }

}
