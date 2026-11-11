import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Gemini-API-Key aus local.properties (nie ins Git-Repo!)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "de.kalorienapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.kalorienapp"
        minSdk = 28
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"

        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\"",
        )

        // Schriftgröße der ganzen App (1.0 = Standard); zum Ausprobieren: -PtextScale=1.1
        buildConfigField("float", "TEXT_SCALE", "${providers.gradleProperty("textScale").getOrElse("1.0")}f")

        // Sichtbare Bereiche der App – ausgeblendete bleiben im Code und in der Datenbank erhalten
        val features = mapOf("WATER" to false, "RECIPES" to false)
        features.forEach { (name, on) ->
            buildConfigField("boolean", "FEATURE_$name", on.toString())
            resValue("bool", "feature_${name.lowercase()}", on.toString())
        }
    }

    buildTypes {
        release {
            // Kleine APK zum Weitergeben: Code und Ressourcen schrumpfen (R8)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Privat-App: Release mit Debug-Key signieren, damit die APK direkt installierbar ist
            // und Debug- und Release-Version sich gegenseitig ohne Datenverlust ersetzen können
            signingConfig = signingConfigs.getByName("debug")
            // `-PcleanTest`: als zweite App daneben installieren → Einrichtung wie ein neuer Nutzer testen,
            // ohne den Speicherstand der eigentlichen App anzufassen
            if (providers.gradleProperty("cleanTest").isPresent) applicationIdSuffix = ".cleantest"
        }
    }

    // Eine APK je Prozessortyp – die Barcode-Erkennung bringt sonst ihre Bibliothek viermal mit.
    // Handys: arm64-v8a (fast alle) bzw. armeabi-v7a (sehr alte), Emulator: x86_64.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.haze.blur)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(libs.junit)
}
