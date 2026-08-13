pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "LumenCrashLocal"
            url = uri(layout.settingsDirectory.asFile.resolve("local-maven"))
        }
        val gprUser = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
        val gprKey = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        if (!gprUser.isNullOrBlank() && !gprKey.isNullOrBlank()) {
            maven {
                name = "GitHubPackagesProjectLumen"
                url = uri("https://maven.pkg.github.com/Chloemlla/Project-Lumen")
                credentials {
                    username = gprUser
                    password = gprKey
                }
            }
        }
    }
}

rootProject.name = "SynapseMobile"
include(":app")
