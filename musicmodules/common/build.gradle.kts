plugins {
    id("com.android.library")
}

android {
    namespace = "com.maxrave.common"
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
    implementation(libs.kermit.logging)
    api(libs.kotlinx.datetime)
    api(libs.uri)

    testImplementation(libs.kotlin.test)
}
