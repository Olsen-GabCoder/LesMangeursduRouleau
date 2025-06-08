// settings.gradle.kts (Ceci est le fichier settings.gradle.kts du PROJET RACINE)
// SITUÉ AU MÊME NIVEAU QUE LE DOSSIER 'app' ET build.gradle.kts RACINE

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
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "Les Mangeurs du Rouleau"
include(":app")