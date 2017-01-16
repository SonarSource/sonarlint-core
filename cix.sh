#!/bin/bash
set -euo pipefail

./set_maven_build_version.sh "$CI_BUILD_NUMBER"

cd its

if [[ $SQ_VERSION = LTS ]]
then
  JAVA_VERSION=3.8
  JAVA_VERSION=3.14  # old? to delete?
  PHP_VERSION=2.7
  JAVASCRIPT_VERSION=2.9
  JAVASCRIPT_VERSION=2.13  # old? to delete?
  PYTHON_VERSION=1.5
  COBOL_VERSION=3.2
else
  JAVA_VERSION=LATEST_RELEASE
  PHP_VERSION=LATEST_RELEASE
  JAVASCRIPT_VERSION=LATEST_RELEASE
  PYTHON_VERSION=LATEST_RELEASE
  COBOL_VERSION=LATEST_RELEASE
fi
echo "Running with SQ=$SQ_VERSION JAVA_VERSION=$JAVA_VERSION JAVASCRIPT_VERSION=$JAVASCRIPT_VERSION PHP_VERSION=$PHP_VERSION PYTHON_VERSION=$PYTHON_VERSION COBOL_VERSION=$COBOL_VERSION"

mvn verify -Prun-its -Dsonar.runtimeVersion=$SQ_VERSION -DjavaVersion=$JAVA_VERSION -DphpVersion=$PHP_VERSION -DjavascriptVersion=$JAVASCRIPT_VERSION -DpythonVersion=$PYTHON_VERSION -DcobolVersion=$COBOL_VERSION
