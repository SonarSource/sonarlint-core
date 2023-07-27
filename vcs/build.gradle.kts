description = "Manage VCS integration"

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.eclipse.jgit:org.eclipse.jgit:6.0.0.202111291000-r")
    implementation(project(":commons"))
    testImplementation(libs.junit.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:6.0.0.202111291000-r")
}