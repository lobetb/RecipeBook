package com.blobet.recipebook.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IngredientParserTest {

    @Test
    fun parse_handlesSimpleFraction() {
        val ingredient = IngredientParser.parse("1/2 cup sugar")
        assertNotNull(ingredient.quantity)
        assertEquals(0.5, ingredient.quantity!!, 0.0001)
        assertEquals("cup", ingredient.unit)
        assertEquals("sugar", ingredient.name)
    }

    @Test
    fun parse_handlesMixedFractionWithSpace() {
        val ingredient = IngredientParser.parse("1 1/4 cups flour")
        assertNotNull(ingredient.quantity)
        assertEquals(1.25, ingredient.quantity!!, 0.0001)
        assertEquals("cups", ingredient.unit)
        assertEquals("flour", ingredient.name)
    }

    @Test
    fun parse_handlesVulgarFractionUnicode() {
        val ingredient = IngredientParser.parse("1½ tsp salt")
        assertNotNull(ingredient.quantity)
        assertEquals(1.5, ingredient.quantity!!, 0.0001)
        assertEquals("tsp", ingredient.unit)
        assertEquals("salt", ingredient.name)
    }

    @Test
    fun parse_ignoresBulletPrefixAndKeepsFraction() {
        val ingredient = IngredientParser.parse("- 3/4 cup milk")
        assertNotNull(ingredient.quantity)
        assertEquals(0.75, ingredient.quantity!!, 0.0001)
        assertEquals("cup", ingredient.unit)
        assertEquals("milk", ingredient.name)
    }
}
