# Run integration tests

# Prerequisites

* Some integration tests load plugins relying on Node.js, so make sure the latest LTS version is installed and `node` is in the PATH.
* SonarQube ITs need Java 17 to run and SonarCloud ITs need Java 11

# First time configuration

1. Make sure your Developer Box is properly setup (see xtranet)
2. Configure Orchestrator settings as described [here](https://github.com/SonarSource/orchestrator#configuration). The Artifactory API key and GitHub token are the only mandatory options. The GH token is a Personal Access Token (classic) with the `repo` scope permission, and SSO properly configured
3. For SonarCloud ITs, make sure the `SONARCLOUD_IT_PASSWORD` env var is defined (you can find the value in our password management tool)
4. Run `mvn clean install` a first time from this `its` folder so that test resources are built (like custom plugins)

# Running ITs from IntelliJ

1. From the root folder of the repository, first build sonarlint-core with `mvn clean install`
2. Make sure the `its` profile is [activated in the Maven tool window](https://www.jetbrains.com/help/idea/work-with-maven-profiles.html#activate_maven_profiles)
3. Reload the project with the top left button of the Maven tool window
4. Open a test class and run it

# Running ITs from command line

1. From the root folder of the repository, first build sonarlint-core with `mvn clean install`
2. Run `mvn verify -f its/pom.xml -Dsonar.runtimeVersion=<SQ server version>`
