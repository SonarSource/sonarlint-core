description = "Extract rules metadata from plugins using CLI"

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation(project(":commons"))
    implementation(project(":plugin-commons"))
    implementation(project(":rule-extractor"))
    implementation("info.picocli:picocli:4.7.0")
    implementation("com.google.code.gson:gson:2.10")
    implementation("org.sonarsource.api.plugin:sonar-plugin-api:10.0.0.695") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation(libs.junit.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("org.slf4j:slf4j-api:2.0.5")
    testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.36.0")

    "sqplugins_test"("org.sonarsource.java:sonar-java-plugin:7.16.0.30901")
    "sqplugins_test"("org.sonarsource.javascript:sonar-javascript-plugin:9.12.1.20358")
    "sqplugins_test"("org.sonarsource.dotnet:sonar-vbnet-plugin:8.51.0.59060")
}
