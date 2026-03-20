package com.blobet.recipebook.ui.home

import androidx.annotation.StringRes
import com.blobet.recipebook.R
import com.blobet.recipebook.domain.model.Recipe

enum class SortMode(@StringRes val labelRes: Int) {
    ALPHA_ASC(R.string.sort_alpha_asc),
    ALPHA_DESC(R.string.sort_alpha_desc),
    NEWEST_FIRST(R.string.sort_newest_first),
    OLDEST_FIRST(R.string.sort_oldest_first),
}

enum class LayoutMode(@StringRes val labelRes: Int) {
    LARGE(R.string.layout_large),
    SMALL(R.string.layout_small),
    LIST(R.string.layout_list),
}

fun List<Recipe>.sortedByMode(mode: SortMode): List<Recipe> {
    return when (mode) {
        SortMode.ALPHA_ASC -> sortedBy { it.title.lowercase() }
        SortMode.ALPHA_DESC -> sortedByDescending { it.title.lowercase() }
        SortMode.NEWEST_FIRST -> sortedByDescending { it.updatedAt }
        SortMode.OLDEST_FIRST -> sortedBy { it.updatedAt }
    }
}
