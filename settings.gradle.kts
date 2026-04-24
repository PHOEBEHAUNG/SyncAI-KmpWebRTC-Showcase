rootProject.name = "VLMWebRTC"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Using local Maven repository for syncai-lib-kmpwebrtc
// Previously used includeBuild for kotlin-webrtc-client development:
//includeBuild("../kotlin-webrtc-client") {
//    dependencySubstitution {
//        substitute(module("com.codingstable:kotlin-webrtc-client")).using(project(":"))
//    }
//}

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // GitHub Packages repository - commented out as we're using local Maven
        maven {
            url = uri("https://maven.pkg.github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC")
            credentials {
                val localProps = java.util.Properties().apply {
                    val propsFile = File(rootDir, "local.properties")
                    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
                }
                // put your github user name and token in local.properties
                username = localProps.getProperty("gpr.user")
                    ?: System.getenv("GITHUB_ACTOR")
                password = localProps.getProperty("gpr.key")
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")