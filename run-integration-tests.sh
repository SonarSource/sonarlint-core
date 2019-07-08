#/bin/bash
#
# ITs need the environment variable 'GITHUB_TOKEN' defined
#

set -ex
SQ_VERSION=$1

plugins_min_versions_path=core/src/main/resources/plugins_min_versions.txt

case "$SQ_VERSION" in
  'LATEST_RELEASE[6.7]')
    minVersions=$(sed -ne '/^[a-z]*=[0-9.]*$/s/$/;/p' < "$plugins_min_versions_path")
    eval "$minVersions"
    JAVA_VERSION=$java
    PHP_VERSION=$php
    # Can't test the version 4.0.0 since there was a change of custom rule API
    JAVASCRIPT_VERSION=4.2.0.6476
    PYTHON_VERSION=$python
    COBOL_VERSION=$cobol
    KOTLIN_VERSION=$kotlin
    RUBY_VERSION=$ruby
    SCALA_VERSION=$sonarscala
    # Because of SONARHTML-91, it is simpler to test with 3.0.1.1444
    WEB_VERSION=3.0.1.1444
    XML_VERSION=$xml
    APEX_VERSION=$sonarapex
    TSQL_VERSION=$tsql
    CPP_VERSION=$cpp
    ;;
  'LATEST_RELEASE[7.9]' | 'LATEST_RELEASE' | 'DEV' | 'DOGFOOD')
    JAVA_VERSION=LATEST_RELEASE
    PHP_VERSION=LATEST_RELEASE
    JAVASCRIPT_VERSION=LATEST_RELEASE
    PYTHON_VERSION=LATEST_RELEASE
    COBOL_VERSION=LATEST_RELEASE
    KOTLIN_VERSION=LATEST_RELEASE
    RUBY_VERSION=LATEST_RELEASE
    SCALA_VERSION=LATEST_RELEASE
    WEB_VERSION=LATEST_RELEASE
    XML_VERSION=LATEST_RELEASE
    APEX_VERSION=LATEST_RELEASE
    TSQL_VERSION=LATEST_RELEASE
    CPP_VERSION=LATEST_RELEASE
    ;;
  *)
    echo "fatal: unknown SQ_VERSION value '$SQ_VERSION'"
    exit 1
esac

export MAVEN_OPTS=-Xmx1024m

cd its
mvn verify -Pits -Dsonar.runtimeVersion=$SQ_VERSION \
    -DjavaVersion=$JAVA_VERSION \
    -DphpVersion=$PHP_VERSION \
    -DjavascriptVersion=$JAVASCRIPT_VERSION \
    -DpythonVersion=$PYTHON_VERSION \
    -DcobolVersion=$COBOL_VERSION \
    -DkotlinVersion=$KOTLIN_VERSION \
    -DrubyVersion=$RUBY_VERSION \
    -DscalaVersion=$SCALA_VERSION \
    -DwebVersion=$WEB_VERSION \
    -DxmlVersion=$XML_VERSION \
    -DapexVersion=$APEX_VERSION \
    -DtsqlVersion=$TSQL_VERSION \
    -DcppVersion=$CPP_VERSION
