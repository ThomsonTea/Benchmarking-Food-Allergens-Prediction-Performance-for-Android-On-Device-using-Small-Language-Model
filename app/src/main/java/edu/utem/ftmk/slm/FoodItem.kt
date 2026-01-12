package edu.utem.ftmk.slm

/**
 * Represents a food item from the dataset (foodpreprocessed.xlsx)
 */
data class FoodItem(
    val id: String = "",
    val name: String = "",
    val ingredients: String = "",
    val allergensRaw: String = "",
    val allergensMapped: String = "",
    val link: String = ""
) {
    // For display in UI
    fun getDisplayName(): String {
        return "$id. $name"
    }
    
    // Check if has allergens
    fun hasAllergens(): Boolean {
        return allergensMapped.isNotEmpty() && allergensMapped != "none"
    }
}
