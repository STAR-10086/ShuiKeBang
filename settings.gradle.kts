pluginManagement {
    repositories {
        // 阿里云镜像在前：国内直连稳定（本机已将其加入代理 nonProxyHosts），海外 runner 也可访问
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // 官方源兜底（不要用 content include 圈定 group，否则会把 KSP 等错误锁死在单一仓库）
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
        // sherpa-onnx Android AAR 通过 JitPack 发布
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ShuiKeBang"
include(":app")
