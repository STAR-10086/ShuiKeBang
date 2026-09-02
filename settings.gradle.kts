pluginManagement {
    repositories {
        // 统一使用官方源：CI 直连；本机走全局 ~/.gradle/gradle.properties 里的 7897 代理
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    // KSP 的 plugin marker 在全新环境偶发解析不稳，直接映射 Maven Central 真实构件，绕开 marker POM
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx Android AAR 通过 JitPack 发布
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ShuiKeBang"
include(":app")
