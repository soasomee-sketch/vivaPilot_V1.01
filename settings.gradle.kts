pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Wtyczka generująca listę licencji open source (Ustawienia -> Informacje prawne ->
    // Licencje open source, sekcja 7 specyfikacji zgodności prawnej z 09.10) nie jest
    // publikowana pod standardowym "plugin marker", więc trzeba jawnie wskazać moduł Maven,
    // z którego ją pobrać (useModule) — bez tego "id(\"com.google.android.gms.oss-licenses-plugin\")"
    // w app/build.gradle.kts by się nie rozwiązał. Współrzędne za oficjalną dokumentacją
    // Google (developers.google.com/android/guides/opensource).
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.android.gms.oss-licenses-plugin") {
                useModule("com.google.android.gms:oss-licenses-plugin:0.13.0")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VivaPilot"
include(":app")
