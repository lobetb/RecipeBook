package com.blobet.recipebook.data.parser

import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeParserTest {

    @Test
    fun parseTextRecipe_handlesFrenchSectionsAndStepPrefixes() {
        val text = """
            Roti de veau en cocotte
            Ingrédients
            - 1 kg de rôti de veau
            - 2 carottes
            - 1 oignon

            Préparation
            Étape 1 - Faites chauffer la cocotte avec un filet d'huile.
            2 - Ajoutez les légumes puis faites revenir 5 minutes.
            3) Couvrez et laissez cuire 45 minutes.
        """.trimIndent()

        val result = RecipeParser.parseTextRecipe(text, "https://example.com/roti")
        val recipe = result.recipe

        assertTrue(recipe != null)
        assertTrue(recipe!!.ingredients.size >= 3)
        assertTrue(recipe.instructions.size >= 2)
        assertTrue(recipe.instructions.any { it.contains("Étape 1") || it.contains("1 -") || it.contains("2 -") })
    }

    @Test
    fun parseHtml_heuristicsExtractFromFrenchArticleBlocks() {
        val html = """
            <html>
              <head>
                <title>Recette du rôti de veau</title>
                <script type="application/ld+json">
                  {"@context":"https://schema.org","@type":"Recipe","name":"Rôti de veau"}
                </script>
              </head>
              <body>
                <article>
                  <h1>Recette du rôti de veau en cocotte</h1>
                  <p>Une recette familiale mijotée.</p>
                  <h2>Ingrédients</h2>
                  <ul>
                    <li>1 kg de rôti de veau</li>
                    <li>2 carottes</li>
                    <li>1 oignon</li>
                  </ul>
                  <h2>Préparation</h2>
                  <p>1 - Faites dorer la viande sur toutes les faces.</p>
                  <p>2 - Ajoutez les légumes puis mouillez légèrement.</p>
                  <p>3 - Couvrez et laissez cuire à feu doux 45 minutes.</p>
                </article>
              </body>
            </html>
        """.trimIndent()

        val result = RecipeParser.parseHtml(html, "https://example.com/roti")
        val recipe = result.recipe

        assertTrue(recipe != null)
        assertTrue(recipe!!.title.lowercase().contains("veau") || recipe.title.lowercase().contains("rôti"))
        assertTrue(recipe.ingredients.isNotEmpty())
        assertTrue(recipe.instructions.isNotEmpty())
    }
}
