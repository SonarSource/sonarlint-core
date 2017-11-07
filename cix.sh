#!/bin/bash
set -euo pipefail

./set_maven_build_version.sh "$CI_BUILD_NUMBER"
./run-integration-tests.sh "$SQ_VERSION"

