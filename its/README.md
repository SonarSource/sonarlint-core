# Run standalone RPC, Server, Cloud and CLI compatibility integration tests

These suites live in `its/`. They exercise standalone RPC, connected mode with SonarQube Server and
SonarQube Cloud, and compatibility with the SonarQube CLI. The default reactor also includes a separate
[RPC integration suite](../README.md#build-and-run-unit-and-integration-tests).

## Prerequisites

* Some integration tests load plugins relying on Node.js, so make sure the latest LTS version is installed and `node` is in the PATH.
* SonarQube integration tests need Java 17 to run and SonarCloud integration tests need Java 11

## First-time configuration

1. Make sure your Developer Box is properly setup (see xtranet)
2. Configure Orchestrator settings as described [here](https://github.com/SonarSource/orchestrator#configuration). The Artifactory API key and GitHub token are the only mandatory options. The GH token is a Personal Access Token (classic) with the `repo` scope permission, and SSO properly configured
3. For SonarCloud integration tests, make sure the `SONARCLOUD_IT_TOKEN` env var is defined (you can find the value in our password management tool)
4. Run `mvn clean install` a first time from this `its` folder so that test resources are built (like custom plugins)

## Running integration tests from IntelliJ

1. From the root folder of the repository, first build sonarlint-core with `mvn clean install`
2. Make sure the `its` profile is [activated in the Maven tool window](https://www.jetbrains.com/help/idea/work-with-maven-profiles.html#activate_maven_profiles)
3. Reload the project with the top left button of the Maven tool window
4. Open a test class and run it

## Running integration tests from the command line

1. From the root folder of the repository, first build sonarlint-core with `mvn clean install`
2. Run `mvn verify -f its/pom.xml -Dsonar.runtimeVersion=<SQ server version>`

## Nightly integration tests

The [Nightly integration tests](../.github/workflows/nightly-integration-tests.yml) workflow runs daily at 03:00 UTC against SonarQube Cloud staging (EU and US) and the latest released SonarQube Server started locally by Orchestrator. Scheduled runs use the default branch; manual runs use the selected branch.

Failures produce test reports and diagnostic artifacts, with Slack notifications for `master`.
