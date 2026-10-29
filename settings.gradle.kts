pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mozilla Maven repository for GeckoView engine
        maven {
            url = uri("https://maven.mozilla.org/maven2/")
            content {
                includeGroup("org.mozilla.geckoview")
            }
        }
    }
}

rootProject.name = "FocusGram"
include(":app")
