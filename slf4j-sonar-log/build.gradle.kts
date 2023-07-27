/** INFO: Dependency should be shaded into core */

description = "Redirect SLF4J logs to sonarlint logging facade"

dependencies {
    implementation(project(":commons"))
}
