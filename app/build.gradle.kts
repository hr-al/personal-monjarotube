plugins { id("com.android.application") }
android {
    namespace = "ru.geelymonjaro.monjarotube"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "ru.geelymonjaro.monjarotube"
        minSdk = 30
        targetSdk = 30
        versionCode = 12
        versionName = "1.0.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    val releaseStore = providers.environmentVariable("MONJAROTUBE_KEYSTORE_FILE").orNull
    if (releaseStore != null) {
        signingConfigs.create("distribution") {
            storeFile = file(releaseStore)
            storePassword = providers.environmentVariable("MONJAROTUBE_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("MONJAROTUBE_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("MONJAROTUBE_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            if (releaseStore != null) signingConfig = signingConfigs.getByName("distribution")
        }
    }
    buildFeatures { buildConfig = true }
    lint { disable += "ExpiredTargetSdkVersion" }
}
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
