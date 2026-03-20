package com.blobet.recipebook.util

import com.google.gson.Gson

object RecipeShareCodec {
    private val gson = Gson()

    fun encode(payload: RecipeSharePayload): String = gson.toJson(payload)

    fun decode(json: String): RecipeSharePayload = gson.fromJson(json, RecipeSharePayload::class.java)
}
