plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.hooshyar.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.hooshyar.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 319
        versionName = "3.19.0"
    }

    // دو نسخه: bazaar (کافه‌بازار) و myket (مایکت) — فقط پرداخت درون‌برنامه‌ای فرق دارد
    flavorDimensions += "store"
    productFlavors {
        create("bazaar") { dimension = "store" }
        create("myket") {
            dimension = "store"
            manifestPlaceholders["marketApplicationId"] = "ir.mservices.market"
            manifestPlaceholders["marketBindAddress"] = "ir.mservices.market.InAppBillingService.BIND"
            manifestPlaceholders["marketPermission"] = "ir.mservices.market.BILLING"
            buildConfigField("String", "MYKET_RSA_KEY", "\"" + (System.getenv("MYKET_RSA_KEY") ?: "") + "\"")
        }
    }

    // امضای انتشار: اگر متغیرهای KEYSTORE_* (از GitHub Secrets) باشند با کلید خودت امضا می‌شود، وگرنه با کلید debug
    val ksPath = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (!ksPath.isNullOrBlank()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (!ksPath.isNullOrBlank()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    val bom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    "bazaarImplementation"("com.github.cafebazaar.Poolakey:poolakey:2.3.0")
    "myketImplementation"("com.github.myketstore:myket-billing-client:1.6")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
