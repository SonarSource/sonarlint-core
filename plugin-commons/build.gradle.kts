/** INFO: Dependency should be shaded into core */

description = "Common code used to load/execute SonarQube plugins"

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation(project(":commons"))
    implementation(project(":plugin-api"))
    implementation("org.sonarsource.api.plugin:sonar-plugin-api:10.0.0.695") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("org.springframework:spring-context:5.3.27")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.codehaus.sonar:sonar-classloader:1.0")
    testCompileOnly("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation(project(":slf4j-sonar-log"))
    testImplementation(project(":commons", "test"))
    testImplementation(libs.junit.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("commons-codec:commons-codec:1.15")
    testImplementation("javax.annotation:javax.annotation-api:1.3.2")
}
