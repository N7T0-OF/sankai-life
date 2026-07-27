import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// ---------------------------------------------------------------------------
// AdMob.
//
// Les builds DEBUG utilisent toujours les identifiants de TEST officiels de
// Google. Ce n'est pas une commodité, c'est une protection : cliquer sur ses
// propres publicités de production fait bannir le compte AdMob, définitivement
// et sans recours.
//
// Les builds RELEASE utilisent les identifiants de production. Ce ne sont pas
// des secrets — ils sont extractibles de n'importe quel APK distribué — mais
// admob.properties permet de les surcharger sans toucher au code.
// ---------------------------------------------------------------------------
val admobProps = Properties().apply {
    val f = rootProject.file("admob.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val admobTestRewardedId = "ca-app-pub-3940256099942544/5224354917"

val admobProdAppId: String = admobProps.getProperty("ADMOB_APP_ID")
    ?: "ca-app-pub-9004438844977083~6279544832"
val admobProdRewardedId: String = admobProps.getProperty("ADMOB_REWARDED_UNIT_ID")
    ?: "ca-app-pub-9004438844977083/8842249130"

// ---------------------------------------------------------------------------
// Signature release : keystore.properties (non versionné). Absent => on signe
// avec la clé debug pour que `assembleRelease` marche quand même en local.
// ---------------------------------------------------------------------------
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.sankailife"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sankailife"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.11.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false

            manifestPlaceholders["admobAppId"] = admobTestAppId
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$admobTestRewardedId\"")
            buildConfigField("boolean", "ADMOB_IS_REAL", "false")
        }
        release {
            // R8 : obscurcit le code (une décompilation ne donne plus des noms
            // lisibles), supprime le code et les ressources inutilisés, et
            // réduit nettement la taille de l'APK.
            isMinifyEnabled = true
            isShrinkResources = true

            manifestPlaceholders["admobAppId"] = admobProdAppId
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$admobProdRewardedId\"")
            buildConfigField("boolean", "ADMOB_IS_REAL", "true")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES") }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Monétisation — pubs récompensées uniquement. L'app reste 100% utilisable
    // sans réseau : voir AdsManager / AdsAvailability.
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
