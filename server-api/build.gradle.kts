/** INFO: Dependency should be shaded into core */

plugins {
    id("com.google.protobuf") version "0.9.4"
}

description = "Interaction with the server through its web API"

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation(project(":commons"))
    implementation(project(":http"))
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("com.google.guava:guava:32.1.1-jre")
    implementation("com.google.code.gson:gson:2.10")
    implementation("com.google.protobuf:protobuf-java:3.21.9")
    implementation("org.sonarsource.sonarqube:sonar-scanner-protocol:7.9") {
        isTransitive = false
    }
    testImplementation(project(":http"))
    testImplementation(project(":commons", "test"))
    testImplementation(libs.junit.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.10")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.0.0-alpha.10")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.1")
    testImplementation("org.slf4j:slf4j-simple:1.7.36")
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("org.apache.httpcomponents.client5:httpclient5:5.2.1") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.9"
    }
}
