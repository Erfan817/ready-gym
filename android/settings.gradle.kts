pluginManagement { repositories { mavenCentral(); google(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral(); google() } }
rootProject.name = "ReadyGym"
include(":core", ":app")
