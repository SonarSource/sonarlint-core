#!/usr/bin/env bash

cd "$(dirname "$0")"/..

update_center_repo=https://github.com/SonarSource/sonar-update-center-properties
plugins_min_versions_path=core/src/main/resources/plugins_min_versions.txt

version=$1
if ! test "$version"; then
    echo usage: $0 version
    echo Example: $0 5.6
    exit 2
fi

cleanup() {
    test -d "$tmpdir" && rm -fr "$tmpdir"
    exit
}
tmpdir=$(mktemp -d)
trap cleanup 0 1 2 3 15

props_repo=$tmpdir/props
git clone "$update_center_repo" "$props_repo"

{
cat << EOF
# Minimum supported analyzer versions: the first versions supported by SonarQube $version,
# as defined in $update_center_repo
EOF
for lang in java php javascript python cobol abap plsql swift rpg cpp; do
    minVersion=$(awk -vv="$version" '
    $0 ~ "\.sqVersions=\[" v {
        sub("\.sqVersions.*", "")
        print
        exit
    }' < "$props_repo/$lang.properties")
    echo "$lang=$minVersion"
done
} | tee "$plugins_min_versions_path"

cat << EOF

Updated $plugins_min_versions_path
Don't forget to rerun tests. You will probably need to update plugins_index.txt

EOF
