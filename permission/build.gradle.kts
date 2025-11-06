import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.mavenPublish)
}

kotlin {

    jvmToolchain(17)
    androidLibrary {
        namespace = "com.biggates.devicemanager.permission"
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
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
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

    val isLocalPublish = gradle.startParameter.taskNames.any { it.contains("publishToMavenLocal") }
    if (!isLocalPublish) {
        signAllPublications()
    }

    coordinates(
        groupId = "com.biggates",
        artifactId = "devicemanager-permission",
        version = "0.0.1",
    )

    pom {
        name = "Device Permission Manager (Kotlin Multiplatform)"
        description = "Device Permission Manager"
        inceptionYear = "2025"
        url = "https://github.com/big-gates/devicemanager-kmp"

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
            url = "https://github.com/big-gates/devicemanager-kmp"
            connection = "scm:git:https://github.com/big-gates/devicemanager-kmp.git"
            developerConnection = "scm:git:ssh://git@github.com/big-gates/devicemanager-kmp.git"
        }
    }
}