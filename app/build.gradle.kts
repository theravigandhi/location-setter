import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun configValue(propertyName: String, placeholder: String, purpose: String): String =
    (localProperties.getProperty(propertyName) ?: System.getenv(propertyName))
        ?: run {
            logger.warn(
                "WARNING: $propertyName not found in local.properties or environment. " +
                    "$purpose won't work correctly until it is provided. " +
                    "See README.md for setup instructions."
            )
            placeholder
        }

val mapsApiKey: String = configValue("MAPS_API_KEY", "MISSING_MAPS_API_KEY", "Maps/Places features")
val upiId: String = configValue("UPI_ID", "your-upi-id@bank", "UPI payment button")
val upiPayeeName: String = configValue("UPI_PAYEE_NAME", "Gandhi Groups", "UPI payment button")
val contactUrl: String = configValue(
    "LICENSE_CONTACT_URL",
    "https://wa.me/910000000000",
    "the \"contact us after payment\" link"
)

android {
    namespace = "com.locationsetter.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.locationsetter.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        buildConfigField("String", "UPI_ID", "\"$upiId\"")
        buildConfigField("String", "UPI_PAYEE_NAME", "\"$upiPayeeName\"")
        buildConfigField("String", "LICENSE_CONTACT_URL", "\"$contactUrl\"")
    }

    signingConfigs {
        getByName("debug") {
            // Checked-in on purpose: this is the standard, non-secret Android debug keystore
            // (password/alias are the universal Android defaults). Pinning it here means every
            // build — CI or local — signs with the same key, so upgrade installs never fail with
            // an "app not installed" signature mismatch the way they would with each machine's
            // own auto-generated ~/.android/debug.keystore.
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.browser)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.places)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.auth.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
