// https://developer.android.com/build#settings-file
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
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
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        maven("https://jogamp.org/deployment/maven")
        maven("https://raw.githubusercontent.com/bravepipeproject/maven-repo/master/repository")
    }
}

rootProject.name = "CloudStream"
include(":app", ":library", ":docs", ":music_full")

include(":musicmodules:kotlinYtmusicScraper")
include(":musicmodules:domain")
include(":musicmodules:common")
include(":musicmodules:ktorExt")
