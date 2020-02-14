package net.taler.wallet.payment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import net.taler.wallet.R
import net.taler.wallet.payment.ProductAdapter.ProductViewHolder


internal class ProductAdapter : RecyclerView.Adapter<ProductViewHolder>() {

    private val items = ArrayList<ContractProduct>()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun setItems(items: List<ContractProduct>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    internal inner class ProductViewHolder(v: View) : ViewHolder(v) {
        private val quantity: TextView = v.findViewById(R.id.quantity)
        private val name: TextView = v.findViewById(R.id.name)
        private val price: TextView = v.findViewById(R.id.price)

        fun bind(product: ContractProduct) {
            quantity.text = product.quantity.toString()
            name.text = product.description
            price.text = product.totalPrice.toString()
        }
    }

}
