/** INFO: Dependency should be shaded into core */

description = "HTTP related code used by SonarLint"

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation(project(":commons"))
    implementation("javax.inject:javax.inject:1")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.github.hakky54:sslcontext-kickstart:8.1.2") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation(project(":commons", "test"))
    testImplementation(libs.junit.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.0")
}
