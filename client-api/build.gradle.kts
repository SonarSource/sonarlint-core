description = "API used to communicate with clients (IDEs)"

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    implementation(project(":commons"))
    implementation(project(":http"))
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.19.0")
    testImplementation(libs.junit.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}
