/** INFO: Dependency should be shaded into core */

description = "Common code for all SonarLint modules"

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation(libs.junit.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.0.0-alpha.10")
}

tasks.processResources {
    expand(mapOf("project_version" to "${project.version}"))
}

/** Make test classes available to other projects */
configurations {
    create("test")
}

tasks.register<Jar>("testArchive") {
    archiveBaseName.set("${project.name}_test")
    from(project.the<SourceSetContainer>()["test"].output)
}

artifacts {
    add("test", tasks["testArchive"])
}
