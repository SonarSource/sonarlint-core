description = "Manage telemetry"

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation(project(":commons"))
    implementation(project(":http"))
    implementation("com.google.code.gson:gson:2.10")
    testImplementation(project(":commons", "test"))
    testImplementation(libs.junit.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.0")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.0.1")
    testImplementation("org.slf4j:slf4j-simple:1.7.36")
}
