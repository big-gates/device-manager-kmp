import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.mavenPublish)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        namespace = "com.biggates.devicemanager"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilations.configureEach {
            compilerOptions.configure {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.ktx)
            implementation(libs.androidx.core.ktx)
            implementation(libs.play.services.location)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.runner)
            implementation(libs.androidx.core)
            implementation(libs.androidx.testExt.junit)
        }
    }

}

mavenPublishing {
    publishToMavenCentral()

//    signAllPublications()

    coordinates(
        groupId = "com.biggates",
        artifactId = "device-manager",
        version = "0.0.1",
    )

    pom {
        name = "Device Manager (Kotlin Multiplatform)"
        description = "Device Info Manager"
        inceptionYear = "2025"
        url = "https://github.com/big-gates/device-manager"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                id = "big-gates"
                name = "Big Gates"
                email = "biggatescorp@gamil.com"
                url = "https://github.com/big-gates"
            }
        }

        scm {
            url = "https://github.com/big-gates/device-manager"
            connection = "scm:git:https://github.com/big-gates/device-manager.git"
            developerConnection = "scm:git:ssh://git@github.com/big-gates/device-manager.git"
        }
    }
}