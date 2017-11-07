#!/bin/bash
set -euo pipefail

plugins_min_versions_path=core/src/main/resources/plugins_min_versions.txt

./set_maven_build_version.sh "$CI_BUILD_NUMBER"
./run-integration-tests.sh "$SQ_VERSION"

