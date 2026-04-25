import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun signingValue(propertyKey: String, envKey: String): String? {
    val fromFile = keystoreProperties.getProperty(propertyKey)?.trim().orEmpty()
    if (fromFile.isNotBlank()) return fromFile
    val fromEnv = System.getenv(envKey)?.trim().orEmpty()
    return fromEnv.ifBlank { null }
}

val releaseStoreFileRaw = signingValue("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")

val releaseSigningReady =
    !releaseStoreFileRaw.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.blobet.recipebook"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blobet.recipebook"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.5.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                val storeFilePath = releaseStoreFileRaw!!.let { path ->
                    if (path.startsWith("~")) {
                        path.replaceFirst("~", System.getProperty("user.home"))
                    } else {
                        path
                    }
                }
                storeFile = file(storeFilePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseSigningReady) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val checkFrenchStringsQuality by tasks.registering {
    group = "verification"
    description = "Prevents degraded French localization text (accents/apostrophes)."

    doLast {
        val file = project.file("src/main/res/values-fr/strings.xml")
        if (!file.exists()) return@doLast

        val content = file.readText(Charsets.UTF_8)
        val contentWithoutPlaceholders = content
            .replace(Regex("%\\d+\\$[a-zA-Z]"), "")
            .replace(Regex("%[a-zA-Z]"), "")

        val suspiciousPlainForms = linkedMapOf(
            "premiere" to "première",
            "diner" to "dîner",
            "etapes" to "étapes",
            "imperial" to "impérial",
            "probleme" to "problème",
            "decrire" to "décrire",
            "echec" to "échec",
        )

        val badWords = mutableListOf<String>()
        suspiciousPlainForms.forEach { (plain, _) ->
            val regex = Regex("""\b${Regex.escape(plain)}\b""", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(contentWithoutPlaceholders)) {
                badWords.add(plain)
            }
        }

        val missingApostrophes = Regex(
            """\b([ldjtmcsnLDJTMCSN])\s+([aeiouhàâäéèêëîïôöùûühAEIOUHÀÂÄÉÈÊËÎÏÔÖÙÛÜH])"""
        )
            .findAll(contentWithoutPlaceholders)
            .map { it.value.trim() }
            .filterNot { it.startsWith("%") }
            .toList()

        if (badWords.isNotEmpty() || missingApostrophes.isNotEmpty()) {
            val message = buildString {
                appendLine("French localization quality check failed in ${file.path}")
                if (badWords.isNotEmpty()) {
                    appendLine("- Suspicious unaccented words found: ${badWords.distinct().joinToString(", ")}")
                }
                if (missingApostrophes.isNotEmpty()) {
                    appendLine("- Suspicious missing apostrophes found: ${missingApostrophes.take(8).joinToString(", ")}")
                }
                appendLine("Please keep accents and French apostrophes in values-fr/strings.xml.")
            }
            throw GradleException(message)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(checkFrenchStringsQuality)
}

val releaseTasksRequested = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true)
}

if (releaseTasksRequested && !releaseSigningReady) {
    throw GradleException(
        """
        Missing release signing configuration.

        Provide signing values via either:
        1) key.properties in project root with keys:
           storeFile, storePassword, keyAlias, keyPassword
        2) Environment variables:
           RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD
        """.trimIndent()
    )
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM (aligned with Material3 1.1.x and compiler extension)
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")

    // Jsoup (HTML parsing)
    implementation("org.jsoup:jsoup:1.17.2")

    // Coil (Image loading for Compose)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
