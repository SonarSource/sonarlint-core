description = "Extract rules metadata from plugins"

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation(project(":commons"))
    implementation(project(":client-api"))
    implementation(project(":plugin-api"))
    implementation(project(":plugin-commons"))
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.sonarsource.sonarqube:sonar-markdown:9.4.0.54424") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("org.sonarsource.api.plugin:sonar-plugin-api:10.0.0.695") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation(project(":commons", "test"))
    testImplementation(libs.junit.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)

    "sqplugins_test"("org.sonarsource.java:sonar-java-plugin:5.13.1.18282")
    "sqplugins_test"("org.sonarsource.javascript:sonar-javascript-plugin:5.2.1.7778")
    "sqplugins_test"("org.sonarsource.typescript:sonar-typescript-plugin:1.9.0.3766")
    "sqplugins_test"("org.sonarsource.php:sonar-php-plugin:3.2.0.4868")
    "sqplugins_test"("org.sonarsource.python:sonar-python-plugin:1.14.0.3086")
    "sqplugins_test"("org.sonarsource.slang:sonar-kotlin-plugin:1.5.0.315")
    "sqplugins_test"("org.sonarsource.slang:sonar-ruby-plugin:1.5.0.315")
    "sqplugins_test"("org.sonarsource.slang:sonar-scala-plugin:1.5.0.315")
    "sqplugins_test"("org.sonarsource.html:sonar-html-plugin:3.1.0.1615")
    "sqplugins_test"("org.sonarsource.xml:sonar-xml-plugin:2.0.1.2020")
}
