/** TODO: Shaded Jar + OSGI bundle + aggregate JaCoCo reports */

plugins {
    java
    id("com.google.protobuf") version "0.9.4"
}


description = "Common library used by some SonarLint flavors"

// The environment variables ARTIFACTORY_PRIVATE_USERNAME and ARTIFACTORY_PRIVATE_PASSWORD are used on CI env
// On local box, please add artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUsername = System.getenv("ARTIFACTORY_PRIVATE_USERNAME")
    ?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_PRIVATE_PASSWORD")
    ?: (if (project.hasProperty("artifactoryPassword")) project.property("artifactoryPassword").toString() else "")

allprojects {
    apply {
        plugin("java")
    }

    group = "org.sonarsource.sonarlint.core"

    repositories {
        mavenCentral {
            content {
                excludeGroupByRegex("com\\.sonarsource.*")
            }
        }
        maven("https://repox.jfrog.io/repox/sonarsource") {
            if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
                credentials {
                    username = artifactoryUsername
                    password = artifactoryPassword
                }
            }
        }
    }

    configurations {
        create("sqplugins_test") { isTransitive = false }
        create("guava_test") { isTransitive = false }
    }

    dependencies {
        "guava_test"("com.google.guava:guava:10.0.1")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    }

    tasks.testClasses {
        doLast {
            copy {
                from(project.configurations["guava_test"])
                into(file("$buildDir/lib"))
                rename { "guava,with,comma.jar" }
            }

            copy {
                from(project.configurations["sqplugins_test"])
                into(file("$buildDir/plugins"))
            }
        }
    }

    tasks.test {
        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(11))
            }
        }
        useJUnitPlatform()
        systemProperty("sonar.plugins.test.path", "${buildDir.absolutePath}/plugins/")
        systemProperty("sonar.lib.test.path", "${buildDir.absolutePath}/lib/")
    }
}

dependencies {
    // TODO: The ones getting shaded should be moved to compileOnly configuration!
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation(project(":commons"))
    implementation(project(":server-api"))
    implementation(project(":telemetry"))
    implementation(project(":issue-tracking"))
    implementation(project(":plugin-commons"))
    implementation(project(":rule-extractor"))
    implementation(project(":analysis-engine"))
    implementation(project(":server-connection"))
    implementation(project(":client-api"))
    implementation(project(":http"))
    implementation("org.jetbrains:annotations:24.0.0")
    implementation("javax.inject:javax.inject:1")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("commons-io:commons-io:2.11.0")
    implementation("commons-codec:commons-codec:1.15")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("com.google.guava:guava:32.1.1-jre")
    implementation("com.google.protobuf:protobuf-java:3.21.9")
    implementation("com.google.code.gson:gson:2.10")
    implementation("org.springframework:spring-context:5.3.27")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.19.0")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("org.sonarsource.api.plugin:sonar-plugin-api:10.0.0.695") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.github.hakky54:sslcontext-kickstart:8.1.2") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("org.apache.httpcomponents.core5:httpcore5:5.2")
    runtimeOnly(project(":vcs"))
    testImplementation(project(":plugin-api"))
    testImplementation(project(":commons", "test"))
    testImplementation(libs.junit.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.0")
    testImplementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.10")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.0.0-alpha.10")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.1")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.0.1")
    testImplementation("org.slf4j:slf4j-simple:1.7.36")
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("org.jetbrains.xodus:xodus-entity-store:2.0.1") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    "sqplugins_test"("org.sonarsource.java:sonar-java-plugin:7.15.0.30507")
    "sqplugins_test"("org.sonarsource.javascript:sonar-javascript-plugin:9.6.0.18814")
    "sqplugins_test"("org.sonarsource.php:sonar-php-plugin:3.23.1.8766")
    "sqplugins_test"("org.sonarsource.python:sonar-python-plugin:4.1.0.11333")
    "sqplugins_test"("org.sonarsource.xml:sonar-xml-plugin:2.6.1.3686")
    "sqplugins_test"("org.sonarsource.text:sonar-text-plugin:2.0.1.611")

    if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
        "sqplugins_test"("com.sonarsource.cpp:sonar-cfamily-plugin:6.18.0.29274")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.9"
    }
}
