plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 传 -PsplitAbi 时按 ABI 拆分并额外产出 universal 全包（CI 发 Release 用）
val splitAbi = project.hasProperty("splitAbi")

android {
    namespace = "com.star.shuikebang"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.star.shuikebang"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
        ndk {
            if (splitAbi) {
                // CI 分架构：只保留两个 ARM 架构，否则 universal 会把 AAR 自带的 x86/x86_64 也打进去
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            } else {
                // 本地常规构建只打 arm64 以提速（debug 另在 buildTypes 追加 x86_64 供模拟器）
                abiFilters += "arm64-v8a"
            }
        }
    }

    // 分架构打包：arm64-v8a / armeabi-v7a 各一个，再加一个 universal 全架构包
    splits {
        abi {
            isEnable = splitAbi
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (!splitAbi) ndk { abiFilters += "x86_64" }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 开发阶段先用 debug 签名产出可安装 release 包；正式发布替换为自有 keystore
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // sherpa AAR 含多 ABI，仅保留目标架构
            keepDebugSymbols += "**/arm64-v8a/*.so"
            // APK 内压缩 so，显著减小下载体积（安装时解压，代价是占用少量安装空间）
            useLegacyPackaging = true
        }
    }

    lint {
        // 产出报告但不因存量样式类问题阻断发版；正确性由编译与单测守护
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Room（仅文本/时间戳，不存音频）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 仅模型下载使用网络
    implementation(libs.okhttp)
    implementation(libs.commons.compress)
    implementation(libs.androidx.datastore.preferences)

    // 离线流式语音识别（JitPack），模型动态下发、不打进 APK
    implementation(libs.sherpa.onnx)

    testImplementation(libs.junit)
}
