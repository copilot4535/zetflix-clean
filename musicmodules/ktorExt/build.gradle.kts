plugins {
    id("com.android.library")
}

android {
    namespace = "com.maxrave.ktorext"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    api(libs.ktor.client.core)
    api(libs.ktor.client.cio)
    implementation(libs.ktor.client.encoding)

    implementation(libs.ktor.client.okhttp)
    implementation(libs.brotli.dec)

    testImplementation(libs.kotlin.test)
}
