plugins {
    id("com.google.protobuf") version "0.9.4"
}

description = "Manage connections with SonarQube or SonarCloud"

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation(project(":commons"))
    implementation(project(":http"))
    implementation(project(":server-api"))
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.apache.commons:commons-compress:1.22")
    implementation("commons-codec:commons-codec:1.15")
    implementation("com.google.protobuf:protobuf-java:3.21.9")
    implementation("org.jetbrains.xodus:xodus-entity-store:2.0.1") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("org.jetbrains.xodus:xodus-environment:2.0.1") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("org.sonarsource.sonarqube:sonar-scanner-protocol:7.9") {
        isTransitive = false
    }
    implementation("org.jetbrains.xodus:xodus-vfs:2.0.1")
    testCompileOnly("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation(project(":commons", "test"))
    testImplementation(libs.junit.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.10")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.0.0-alpha.10")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.1")
    testImplementation("org.slf4j:slf4j-simple:1.7.36")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.9"
    }
}
