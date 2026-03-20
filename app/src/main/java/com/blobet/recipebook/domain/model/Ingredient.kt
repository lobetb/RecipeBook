package com.blobet.recipebook.domain.model

data class Ingredient(
    val id: Long = 0,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val orderIndex: Int = 0,
) {
    fun scaled(originalServings: Int, desiredServings: Int): Ingredient {
        if (quantity == null || originalServings <= 0) return this
        val factor = desiredServings.toDouble() / originalServings.toDouble()
        return copy(quantity = quantity * factor)
    }

    fun displayQuantity(): String {
        val qty = quantity ?: return ""
        // Display as fraction-friendly format
        return when {
            qty == qty.toLong().toDouble() -> qty.toLong().toString()
            else -> String.format("%.2f", qty).trimEnd('0').trimEnd('.')
        }
    }

    fun displayText(): String {
        val parts = mutableListOf<String>()
        val qtyStr = displayQuantity()
        if (qtyStr.isNotEmpty()) parts.add(qtyStr)
        if (!unit.isNullOrBlank()) parts.add(unit)
        parts.add(name)
        return parts.joinToString(" ")
    }
}
