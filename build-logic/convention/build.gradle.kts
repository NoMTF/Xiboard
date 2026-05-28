// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version embeddedKotlinVersion
}

group = "com.xiboard.build_logic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    implementation(libs.kotlinx.serialization.json)
}

gradlePlugin {
    plugins {
        register("androidAppConvention") {
            id = "com.xiboard.app-convention"
            implementationClass = "AndroidAppConventionPlugin"
        }
        register("dataChecksums") {
            id = "com.xiboard.data-checksums"
            implementationClass = "DataChecksumsPlugin"
        }
        register("nativeAppConvention") {
            id = "com.xiboard.native-app-convention"
            implementationClass = "NativeAppConventionPlugin"
        }
        register("nativeCacheHash") {
            id = "com.xiboard.native-cache-hash"
            implementationClass = "NativeCacheHashPlugin"
        }
        register("openccData") {
            id = "com.xiboard.opencc-data"
            implementationClass = "OpenCCDataPlugin"
        }
    }
}
