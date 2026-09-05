import org.gradle.api.plugins.ExtensionAware

// https://developer.android.com/build#settings-file
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

gradle.allprojects {
    listOf(buildscript.dependencies, dependencies).forEach {
        if (it is ExtensionAware) {
            it.extensions.add("mapPath", object : groovy.lang.Closure<String>(it) {
                @Suppress("unused")
                fun doCall(path: String): String = path
            })
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
    }
}

rootProject.name = "CloudStream"
include(":app", ":library", ":docs")

include(":musicmodules:kotlinYtmusicScraper")
include(":musicmodules:domain")
include(":musicmodules:common")
include(":musicmodules:ktorExt")
