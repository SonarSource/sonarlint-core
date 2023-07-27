description = "API used between SonarLint and analyzers"

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.sonarsource.api.plugin:sonar-plugin-api:10.0.0.695") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
}

tasks.processResources {
    expand(mapOf("project_version" to "${project.version}"))
}
