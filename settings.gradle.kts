rootProject.name = "sonarlint-core"
include("analysis-engine", "client-api", "commons", "http", "issue-tracking", /*"its", "osgi",*/
    "plugin-api", "plugin-commons", "rule-extractor", "rule-extractor-cli", "server-api", "server-connection",
    "slf4j-sonar-log", "telemetry", "vcs")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            val junit5 = version("junit5", "5.9.1")
            library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef(junit5)
            library("junit-engine", "org.junit.jupiter", "junit-jupiter-engine").versionRef(junit5)
            // Needed for https://github.com/gradle/gradle/issues/22333
            library("junit-launcher", "org.junit.platform:junit-platform-launcher:1.9.2")
            library("assertj-core", "org.assertj:assertj-core:3.23.1")
            library("mockito-core", "org.mockito:mockito-core:4.9.0")
        }
    }
}

val isCiServer = System.getenv().containsKey("CIRRUS_CI")
val isMasterBranch = System.getenv()["CIRRUS_BRANCH"] == "master"
val buildCacheHost: String = System.getenv().getOrDefault("CIRRUS_HTTP_CACHE_HOST", "localhost:12321")
buildCache {
    local {
        isEnabled = !isCiServer
    }
    remote<HttpBuildCache> {
        url = uri("http://${buildCacheHost}/")
        isEnabled = isCiServer
        isPush = isMasterBranch
    }
}
