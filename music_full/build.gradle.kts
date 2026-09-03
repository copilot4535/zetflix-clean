plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.maxrave.simpmusic"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.okio)
    implementation(libs.uri)

    // Compose
    implementation(libs.runtime)
    implementation(libs.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material.ripple)

    // Koin
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.androidx.compose)

    // Network
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.encoding)

    // Coil
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kmpalette.core)
    implementation(libs.kmpalette.network)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.androidx.room.migration)
    implementation(libs.room.ktx)

    // Media3
    implementation(libs.androidx.media)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)

    // Others
    implementation(libs.datastore.preferences)
    implementation(libs.compottie)
    implementation(libs.paging.compose)
    implementation(libs.androidx.paging.common)
    implementation(libs.haze)
    implementation(libs.haze.material)
    implementation(libs.liquid.glass)
    implementation(libs.liquid.glass.shape)
    implementation(libs.cmptoast)
    implementation(libs.file.picker)
    implementation(libs.markdown)
    
    // Services specific
    implementation(libs.ksoup.html)
    implementation(libs.ksoup.entities)
    implementation(libs.common.codec)
    implementation(libs.kotlin.onetimepassword)
    implementation(libs.kuromoji.ipadic)
    implementation(libs.pinyin4j)
    implementation(libs.quickjs)
    
    implementation(libs.activity.compose)
    implementation(libs.constraintlayout.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.startup.runtime)
    implementation(libs.easypermissions)
    implementation(libs.legacy.support.v4)
    implementation(libs.coroutines.android)
    implementation(libs.glance)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.webkit)
    implementation(libs.ffmpeg.kit.audio)
}
