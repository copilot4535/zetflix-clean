@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.gradle.internal.tasks.CompileArtProfileTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    android {
        namespace = "com.maxrave.kotlinytmusicscraper"
        compileSdk = 37
        minSdk = 23
    }

    jvm {
    }

    sourceSets {
        commonMain {
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
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                // implementation(libs.ffmpeg.kit.audio) // Conflicting minSdkVersion and native libs
                implementation(libs.gson)
                implementation(libs.newpipeextractor)
                implementation(libs.nicehttp)
            }
        }

        jvmMain {
            dependencies {
                // implementation(libs.nicehttp)
            }
        }
    }
}

configurations.all {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
}

tasks.withType<CompileArtProfileTask> {
    enabled = false
}
