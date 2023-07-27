description = "Run analysis"

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation(project(":commons"))
    implementation(project(":plugin-api"))
    implementation(project(":plugin-commons"))
    implementation("org.apache.commons:commons-csv:1.9.0")
    implementation("commons-codec:commons-codec:1.15")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.springframework:spring-context:5.3.27")
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.sonarsource.api.plugin:sonar-plugin-api:10.0.0.695") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testCompileOnly("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation(project(":commons", "test"))
    testImplementation(libs.junit.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("org.jetbrains:annotations:24.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.1")
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("org.mockito:mockito-junit-jupiter:4.9.0")
}
