#!/usr/bin/env bash

cd "$(dirname "$0")"/..

update_center_repo=https://github.com/SonarSource/sonar-update-center-properties
plugins_min_versions_path=core/src/main/resources/plugins_min_versions.txt
plugins_index_test_path=core/src/test/resources/validate/plugins_index.txt

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

sorted_version_lines() {
    grep -F .sqVersions= "$1" | sort -t. -k 1,1n -k 2,2n -k 3,3n -k 4,4n
}

get_min_plugin_version() {
    sorted_version_lines "$1" | \
    awk -vv="$version" '
    $0 ~ "\.sqVersions=\[" v "," {
        sub("\.sqVersions.*", "")
        print
        exit
    }'
}

print_plugins_min_versions() {
    echo "# Minimum supported analyzer versions: the first versions supported by SonarQube $version,"
    echo "# as defined in $update_center_repo"
    for plugin in "${plugins[@]}"; do
        echo "$plugin=${minVersions[$plugin]}"
    done
}

rewrite_plugins_index_test() {
    for plugin in "${plugins[@]}"; do
        minVersion=${minVersions[$plugin]}
        sed -i "s/$plugin,.*/$plugin,sonar-$plugin-plugin-$minVersion.jar|dummy-hash/" "$plugins_index_test_path"
    done
}

plugins=(java php javascript python cobol abap plsql swift rpg cpp)
declare -A minVersions
for plugin in "${plugins[@]}"; do
    minVersions[$plugin]=$(get_min_plugin_version "$props_repo/$plugin.properties")
done

print_plugins_min_versions | tee "$plugins_min_versions_path"
rewrite_plugins_index_test

cat << EOF

Rewrote these files with the current minimum versions:

- $plugins_min_versions_path
- $plugins_index_test_path

EOF
