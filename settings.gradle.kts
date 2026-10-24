enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// 国内本地构建优先走阿里云镜像；GitHub Actions 的 runner 在境外，访问阿里云镜像经常超时，
// 直连官方仓库反而更稳，所以 CI 上只用官方仓库。
pluginManagement {
    repositories {
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google()
        mavenCentral()
        maven("https://api.xposed.info/")
        maven("https://s01.oss.sonatype.org/content/repositories/releases/")
        maven("https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.8.0")
}

rootProject.name = "Xiaobaitiao"
include(":app")
