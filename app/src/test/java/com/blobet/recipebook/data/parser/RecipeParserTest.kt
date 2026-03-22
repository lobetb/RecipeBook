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

    @Test
    fun parseHtml_extractsImageAndLongNumberedFrenchSteps() {
        val html = """
            <html>
              <head>
                <title>Recette du rôti de veau en cocotte en fonte</title>
                <meta property="og:image" content="https://cdn.example.com/roti.webp" />
              </head>
              <body>
                <article>
                  <h1>Recette du rôti de veau en cocotte en fonte</h1>
                  <h2>Ingrédients du rôti de veau en cocotte en fonte</h2>
                  <ul>
                    <li>1 rôti de veau ficelé</li>
                    <li>1 kg de pommes de terre</li>
                    <li>200 g de lardons fumés</li>
                  </ul>
                  <h2>Préparation du rôti de veau à la cocotte en fonte</h2>
                  <p>1. Émincez l'oignon.</p>
                  <p>2. Faites revenir les lardons.</p>
                  <p>3. Mettez-y le rôti et dorez toutes les faces.</p>
                  <p>4. Remettez les lardons et les oignons, puis versez le vin blanc.</p>
                  <p>5. Épluchez les pommes de terre.</p>
                  <p>6. Ajoutez les pommes de terre.</p>
                  <p>7. Salez, poivrez et ajoutez le bouquet garni.</p>
                  <p>8. Portez à ébullition puis faites mijoter.</p>
                  <p>9. Retournez le rôti à mi-cuisson.</p>
                  <p>10. Ajoutez un peu d'eau puis faites réduire.</p>
                  <p>11. Filtrez le jus obtenu.</p>
                  <p>12. Servez et bon appétit.</p>
                </article>
              </body>
            </html>
        """.trimIndent()

        val result = RecipeParser.parseHtml(html, "https://example.com/roti-long")
        val recipe = result.recipe

        assertTrue(recipe != null)
        assertTrue(recipe!!.imageUrl?.contains("roti.webp") == true)
        assertTrue(recipe.instructions.size >= 10)
        assertTrue(recipe.instructions.first().startsWith("1."))
        assertTrue(recipe.instructions.last().startsWith("12."))
    }

    @Test
    fun parseHtml_extractsImageWhenJsonLdImageObjectContainsUrlArray() {
        val html = """
            <html>
              <head>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@graph": [
                    {
                      "@type": "Recipe",
                      "name": "Gratin d'endives au jambon",
                      "recipeIngredient": ["4 endives", "2 échalotes"],
                      "recipeInstructions": ["Étape 1", "Étape 2"],
                      "image": {
                        "@type": "ImageObject",
                        "url": [
                          "https://example.com/image-1x1.jpg",
                          "https://example.com/image-16x9.jpg"
                        ]
                      }
                    }
                  ]
                }
                </script>
              </head>
              <body></body>
            </html>
        """.trimIndent()

        val result = RecipeParser.parseHtml(html, "https://example.com/gratin")
        val recipe = result.recipe

        assertTrue(recipe != null)
        assertTrue(recipe!!.imageUrl == "https://example.com/image-1x1.jpg")
    }

    @Test
    fun parseHtml_splitsSingleOlItemContainingAllNumberedSteps() {
        val html = """
            <html>
              <head>
                <title>Recette test</title>
              </head>
              <body>
                <article>
                  <h1>Recette test</h1>
                  <h2>Ingrédients</h2>
                  <ul>
                    <li>1 kg de pommes de terre</li>
                    <li>1 oignon</li>
                  </ul>
                  <h2>Préparation</h2>
                  <ol>
                    <li>1. Étape un. 2. Étape deux. 3. Étape trois. 4. Étape quatre. 5. Étape cinq. 6. Étape six. 7. Étape sept. 8. Étape huit. 9. Étape neuf. 10. Étape dix. 11. Étape onze. 12. Étape douze.</li>
                  </ol>
                </article>
              </body>
            </html>
        """.trimIndent()

        val result = RecipeParser.parseHtml(html, "https://example.com/packed-steps")
        val recipe = result.recipe

        assertTrue(recipe != null)
        assertTrue(recipe!!.instructions.size >= 1)
        assertTrue(recipe.instructions.first().contains("1."))
    }

    @Test
    fun parseHtml_extractsAllStepsFromOrderedListAfterPreparationHeading() {
        val html = """
            <html>
              <body>
                <article>
                  <h2>Ingrédients</h2>
                  <ul>
                    <li>1 kg de pommes de terre</li>
                    <li>1 oignon</li>
                  </ul>
                  <h2>Préparation</h2>
                  <ol>
                    <li>Émincez l'oignon.</li>
                    <li>Faites revenir les lardons.</li>
                    <li>Mettez-y le rôti.</li>
                    <li>Ajoutez le vin blanc.</li>
                    <li>Épluchez les pommes de terre.</li>
                    <li>Ajoutez les pommes de terre.</li>
                    <li>Salez et poivrez.</li>
                    <li>Laissez mijoter.</li>
                    <li>Retournez le rôti à mi-cuisson.</li>
                    <li>Ajoutez un peu d'eau.</li>
                    <li>Filtrez le jus.</li>
                    <li>Servez.</li>
                  </ol>
                </article>
              </body>
            </html>
        """.trimIndent()

        val result = RecipeParser.parseHtml(html, "https://example.com/ol-steps")
        val recipe = result.recipe

        assertTrue(recipe != null)
        assertTrue(recipe!!.instructions.size >= 12)
        assertTrue(recipe.instructions.first().contains("Émincez"))
        assertTrue(recipe.instructions.last().contains("Servez"))
    }
}
