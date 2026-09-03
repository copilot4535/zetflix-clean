import com.android.build.gradle.internal.tasks.CompileArtProfileTask

plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.maxrave.kotlinytmusicscraper"
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
    implementation(project(":musicmodules:domain"))
    implementation(project(":musicmodules:common"))
    implementation(project(":musicmodules:ktorExt"))
    implementation(libs.okio)
    implementation(libs.kotlinx.datetime)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.reflect)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.xml)
    implementation(libs.ktor.serialization.kotlinx.protobuf)

    implementation(libs.ksoup.html)
    implementation(libs.ksoup.entities)
    implementation(libs.quickjs)

    implementation(libs.gson)
    implementation(libs.newpipeextractor)
    implementation(libs.nicehttp)

    testImplementation(libs.kotlin.test)
}

configurations.all {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
}

tasks.withType<CompileArtProfileTask> {
    enabled = false
}
