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
        namespace = "com.biggates.devicemanager.device"
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
            api(projects.permission)
            implementation(libs.kotlinx.coroutines.core)
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

    val isLocalPublish = gradle.startParameter.taskNames.any { it.contains("publishToMavenLocal") }
    if (!isLocalPublish) {
        signAllPublications()
    }

    coordinates(
        groupId = "io.github.big-gates",
        artifactId = "devicemanager-device",
        version = "0.0.1",
    )

    pom {
        name.set("DeviceManager Device")
        description.set(
            "A Kotlin Multiplatform library for accessing device information and monitoring on Android and iOS. " +
            "Includes location tracking, device info, and integrates with devicemanager-permission module."
        )
        inceptionYear.set("2025")
        url.set("https://github.com/big-gates/kmp-device-manager")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("big-gates")
                name.set("Big Gates")
                email.set("biggatescorp@gmail.com")
                url.set("https://github.com/big-gates")
                organization.set("Big Gates")
                organizationUrl.set("https://github.com/big-gates")
            }
        }

        scm {
            url.set("https://github.com/big-gates/kmp-device-manager")
            connection.set("scm:git:git://github.com/big-gates/kmp-device-manager.git")
            developerConnection.set("scm:git:ssh://git@github.com/big-gates/kmp-device-manager.git")
        }

        issueManagement {
            system.set("GitHub Issues")
            url.set("https://github.com/big-gates/kmp-device-manager/issues")
        }
    }
}