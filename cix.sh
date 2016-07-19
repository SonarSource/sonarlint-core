#!/bin/bash
set -euo pipefail
echo "Running with SQ=$SQ_VERSION JAVA_VERSION=$JAVA_VERSION JAVASCRIPT_VERSION=$JAVASCRIPT_VERSION PHP_VERSION=$PHP_VERSION PYTHON_VERSION=$PYTHON_VERSION"

#deploy the version built by travis
CURRENT_VERSION=`mvn help:evaluate -Dexpression="project.version" | grep -v '^\[\|Download\w\+\:'`
RELEASE_VERSION=`echo $CURRENT_VERSION | sed "s/-.*//g"`
NEW_VERSION="$RELEASE_VERSION-build$CI_BUILD_NUMBER"
echo $NEW_VERSION
mkdir -p core/target
cd target
curl --user $ARTIFACTORY_QA_READER_USERNAME:$ARTIFACTORY_QA_READER_PASSWORD -sSLO https://repox.sonarsource.com/sonarsource-public-qa/org/sonarsource/scanner/maven/sonar-maven-plugin/$NEW_VERSION/sonar-maven-plugin-$NEW_VERSION.jar
cd ..

mvn install:install-file -Dfile=core/target/sonarlint-core-$NEW_VERSION.jar


mvn verify -Prun-its -Dsonar.runtimeVersion=$SQ_VERSION -DjavaVersion=$JAVA_VERSION -DphpVersion=$PHP_VERSION -DjavascriptVersion=$JAVASCRIPT_VERSION -DpythonVersion=$PYTHON_VERSION
