package com.blobet.recipebook.domain.model

data class Recipe(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val sourceUrl: String? = null,
    val imageUrl: String? = null,
    val localImagePath: String? = null,
    val servings: Int = 4,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val instructions: List<String> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val totalTimeMinutes: Int?
        get() {
            val prep = prepTimeMinutes
            val cook = cookTimeMinutes
            return when {
                prep != null && cook != null -> prep + cook
                prep != null -> prep
                cook != null -> cook
                else -> null
            }
        }

    val displayImage: String?
        get() = localImagePath ?: imageUrl
}
