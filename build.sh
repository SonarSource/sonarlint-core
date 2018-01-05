#!/bin/bash

set -euo pipefail

function maven_expression() {
  mvn help:evaluate -Dexpression=$1 | grep -v '^\[\|Download\w\+\:'
}

function set_maven_build_version() {
  BUILD_ID=$1
  CURRENT_VERSION=`maven_expression "project.version"`
  RELEASE_VERSION=`echo $CURRENT_VERSION | sed "s/-.*//g"`

  # In case of 2 digits, we need to add the 3rd digit (0 obviously)
  # Mandatory in order to compare versions (patch VS non patch)
  IFS=$'.'
  DIGIT_COUNT=`echo $RELEASE_VERSION | wc -w`
  unset IFS
  if [ $DIGIT_COUNT -lt 3 ]; then
      RELEASE_VERSION="$RELEASE_VERSION.0"
  fi
  NEW_VERSION="$RELEASE_VERSION.$BUILD_ID"
  
  echo "Replacing version $CURRENT_VERSION with $NEW_VERSION"
  mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -DnewVersion=$NEW_VERSION -DgenerateBackupPoms=false -B -e
  # Used later on for the release
  export PROJECT_VERSION=$NEW_VERSION
}

export PATH=`pwd`/.local/bin:$PATH

if [ "${TRAVIS_BRANCH}" == "master" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
  echo '======= Build, deploy and analyze master'

  # Analyze with SNAPSHOT version as long as SQ does not correctly handle
  # purge of release data
  CURRENT_VERSION=`maven_expression "project.version"`

  set_maven_build_version "$TRAVIS_BUILD_NUMBER"
  
  export MAVEN_OPTS="-Xmx1536m -Xms128m"
  mvn org.jacoco:jacoco-maven-plugin:prepare-agent deploy sonar:sonar \
      -Pcoverage-per-test,deploy-sonarsource,release,sign \
      -Dsonarsource.keystore.path=$SONARSOURCE_KEYSTORE_PATH \
      -Dsonarsource.keystore.password=$SONARSOURCE_KEYSTORE_PASS \
      -Dmaven.test.redirectTestOutputToFile=false \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN \
      -Dsonar.projectVersion=$CURRENT_VERSION \
      -Dsonar.analysis.buildNumber=$BUILD_ID \
      -Dsonar.analysis.pipeline=$BUILD_ID \
      -Dsonar.analysis.sha1=$GIT_SHA1  \
      -Dsonar.analysis.repository=$GITHUB_REPO \
      -DargLine="-Xmx1536m"
      -B -e -V $*

elif [[ "${TRAVIS_BRANCH}" == "branch-"* ]] && [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
  # no dory analysis on release branch

  export MAVEN_OPTS="-Xmx1536m -Xms128m"

  # get current version from pom
  CURRENT_VERSION=`maven_expression "project.version"`
  
  if [[ $CURRENT_VERSION =~ "-SNAPSHOT" ]]; then
    echo "======= Found SNAPSHOT version ======="
    # Do not deploy a SNAPSHOT version but the release version related to this build
    set_maven_build_version $TRAVIS_BUILD_NUMBER
    mvn deploy \
      -Pdeploy-sonarsource,release,sign \
      -Dsonarsource.keystore.path=$SONARSOURCE_KEYSTORE_PATH \
      -Dsonarsource.keystore.password=$SONARSOURCE_KEYSTORE_PASS \
      -DargLine="-Xmx1536m" \
      -B -e -V $*
  else
    echo "======= Found RELEASE version ======="
    mvn deploy \
      -Pdeploy-sonarsource,release,sign \
      -Dsonarsource.keystore.path=$SONARSOURCE_KEYSTORE_PATH \
      -Dsonarsource.keystore.password=$SONARSOURCE_KEYSTORE_PASS \
      -DargLine="-Xmx1536m" \
      -B -e -V $*
  fi

elif [ "$TRAVIS_PULL_REQUEST" != "false" ] && [ -n "${GITHUB_TOKEN:-}" ]; then
  echo '======= Build and analyze pull request'
  
  # Do not deploy a SNAPSHOT version but the release version related to this build and PR
  set_maven_build_version "$TRAVIS_BUILD_NUMBER"

  # No need for Maven phase "install" as the generated JAR files do not need to be installed
  # in Maven local repository. Phase "verify" is enough.

  export MAVEN_OPTS="-Xmx1G -Xms128m"
  echo '======= with deploy'
  mvn org.jacoco:jacoco-maven-plugin:prepare-agent deploy sonar:sonar \
      -Pdeploy-sonarsource \
      -Dmaven.test.redirectTestOutputToFile=false \
      -Dsonar.analysis.mode=issues \
      -Dsonar.github.pullRequest=$TRAVIS_PULL_REQUEST \
      -Dsonar.github.repository=$TRAVIS_REPO_SLUG \
      -Dsonar.github.oauth=$GITHUB_TOKEN \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN \
      -DargLine="-Xmx1536m" \
      -B -e -V $*

  mvn sonar:sonar \
      -Dsonar.host.url=$SONAR_HOST_URL \
      -Dsonar.login=$SONAR_TOKEN \
      -Dsonar.analysis.buildNumber=$BUILD_ID \
      -Dsonar.analysis.pipeline=$BUILD_ID \
      -Dsonar.analysis.sha1=$GIT_SHA1  \
      -Dsonar.analysis.repository=$GITHUB_REPO \
      -Dsonar.analysis.prNumber=$PULL_REQUEST \
      -Dsonar.branch.name=$GITHUB_BASE_BRANCH \
      -Dsonar.branch.target=$GITHUB_TARGET_BRANCH \
      -DargLine="-Xmx1536m" \
      -B -e -V
  
else
  echo '======= Build, no analysis, no deploy'

  # No need for Maven phase "install" as the generated JAR files do not need to be installed
  # in Maven local repository. Phase "verify" is enough.

  mvn verify \
      -Dmaven.test.redirectTestOutputToFile=false \
      -DargLine="-Xmx1536m" \
      -B -e -V $*

  #in order to stop QA from starting, the build.properties file is removed
  rm build.properties
fi
