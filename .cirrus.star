# Cirrus
load("cirrus", "yaml", "fs")
# RE
load("github.com/SonarSource/cirrus-modules@v2", "load_features")
# Languages
load(
    "github.com/SonarSource/cirrus-modules/languages/lib.star@experimental/osc/sonarsec-4714-module",
    "inject_3rd_apis_conf"
)
load(
    "github.com/SonarSource/cirrus-modules/languages/tasks/build.star@experimental/osc/sonarsec-4714-module",
    "build_task_mvn_conf_factory"
)


def merge_conf_into(target, spec):
    for key in spec.keys():
        if target.get(key) == None:
            target.update({key: spec[key]})
        else:
            target[key].update(spec[key])


# POC: by refactoring the env_conf only, we can benefit from shared credentials configuration
def env_conf():
    values = dict()
    inject_3rd_apis_conf(
        values,
        requires_burgr=True,
        requires_next=True,
        requires_pgp_signing=True,
        requires_repox_deployer=True,
        requires_repox_promoter=True
    )
    values.update({
        'CIRRUS_CLONE_DEPTH': '50',
        'CIRRUS_SHELL': 'bash',
        #'SONAR_HOST_URL': '${SONARQUBE_NEXT_HOST_URL}',
        #'SONAR_TOKEN': '${SONARQUBE_NEXT_TOKEN}'
        "SONAR_HOST_URL": "VAULT[development/kv/data/next data.url]",
        "SONAR_TOKEN": "VAULT[development/kv/data/next data.token]"
    })
    return {"env": values}


def maven_cache_conf():
    return {
        'folder': '${CIRRUS_WORKING_DIR}/.m2/repository',
        'fingerprint_script': [
            "find . -name pom.xml -not -path './its/*' -exec cat {} \\+"
        ]
    }


def eks_container_conf_factory(dockerfile='.cirrus/Dockerfile', cpu=4, memory="2G"):
    return {
        'dockerfile': dockerfile,
        'docker_arguments': {
            'CIRRUS_AWS_ACCOUNT': '${CIRRUS_AWS_ACCOUNT}',
            'JDK_VERSION': '${JDK_VERSION}'
        },
        'region': 'eu-central-1',
        'cluster_name': '${CIRRUS_CLUSTER_NAME}',
        'builder_role': 'cirrus-builder',
        'builder_image': 'docker-builder-v*',
        'builder_instance_type': 't3.xlarge',
        'builder_subnet_id': '${CIRRUS_AWS_SUBNET}',
        'namespace': 'default',
        'cpu': cpu,
        'memory': memory
    }


def build_task_conf():
    return build_task_mvn_conf_factory(
        env={
            'JDK_VERSION': '11',
            'DEPLOY_PULL_REQUEST': 'true',
            'ARTIFACTORY_DEPLOY_REPO': 'sonarsource-public-qa',
        },
        eks_container=eks_container_conf_factory(),
        before_build={
            "maven_cache": maven_cache_conf()
        },
        build_args=[
            "-Dmaven.test.skip=true",
            "-Dsonar.skip=true"
        ],
        after_build={
            "cache_script": ['mvn -B -e -V -Pits dependency:go-offline'],
            "cleanup_before_cache_script": 'cleanup_maven_repository'
        }
    )


def test_linux_task_conf():
    return {
        "test_linux_task": {
            'skip': "changesIncludeOnly('docs/**/*', 'spec/**/*', 'README.md')",
            'only_if': '$CIRRUS_USER_COLLABORATOR == \'true\' && $CIRRUS_TAG == "" && $CIRRUS_BUILD_SOURCE != "cron" && ($CIRRUS_PR != "" || $CIRRUS_BRANCH == $CIRRUS_DEFAULT_BRANCH || $CIRRUS_BRANCH =~ "branch-.*" || $CIRRUS_BRANCH =~ "dogfood-on-.*")',
            'maven_cache': maven_cache_conf(),
            'depends_on': ['build'],
            'eks_container': eks_container_conf_factory(),
            'env': {'JDK_VERSION': '17', 'DEPLOY_PULL_REQUEST': 'false'},
            'script': [
                'source cirrus-env QA',
                'PULL_REQUEST_SHA=$GIT_SHA1 regular_mvn_build_deploy_analyze -P-deploy-sonarsource,-release,-sign -Dcommercial -Dmaven.shade.skip=true -Dmaven.install.skip=true -Dmaven.deploy.skip=true -Dsonar.coverage.jacoco.xmlReportPaths=$CIRRUS_WORKING_DIR/report-aggregate/target/site/jacoco-aggregate/jacoco.xml'
            ],
            'cleanup_before_cache_script': 'cleanup_maven_repository',
            'on_failure': {
                'junit_artifacts': {
                    'path': '**/target/surefire-reports/TEST-*.xml',
                    'format': 'junit'}
            }
        }
    }


def test_windows_task_conf():
    return {
        "test_windows_task": {
            'skip': "changesIncludeOnly('docs/**/*', 'spec/**/*', 'README.md')",
            'only_if': '$CIRRUS_USER_COLLABORATOR == \'true\' && $CIRRUS_TAG == "" && $CIRRUS_BUILD_SOURCE != "cron" && ($CIRRUS_PR != "" || $CIRRUS_BRANCH == $CIRRUS_DEFAULT_BRANCH || $CIRRUS_BRANCH =~ "branch-.*" || $CIRRUS_BRANCH =~ "dogfood-on-.*")',
            'depends_on': ['build'],
            'ec2_instance': {
                'experimental': True,
                'image': 'base-windows-jdk17-v*',
                'platform': 'windows',
                'region': 'eu-central-1',
                'subnet_id': '${CIRRUS_AWS_SUBNET}',
                'type': 't3.xlarge'
            },
            'env': {'MAVEN_OPTS': '-Xmx4G'},
            'maven_cache': {'folder': '${CIRRUS_WORKING_DIR}/.m2/repository'},
            'script': [
                'source cirrus-env QA',
                'source set_maven_build_version $BUILD_NUMBER',
                'mvn -B -e -V verify -Dcommercial -Dmaven.test.redirectTestOutputToFile=false'
            ],
            'cleanup_before_cache_script': 'cleanup_maven_repository',
            'on_failure': {
                'junit_artifacts': {
                    'path': '**/target/surefire-reports/TEST-*.xml',
                    'format': 'junit'
                }
            }
        }
    }


def mend_scan_task_conf():
    return {
        "mend_scan_task": {
            'skip': "changesIncludeOnly('docs/**/*', 'spec/**/*', 'README.md')",
            'only_if': '$CIRRUS_USER_COLLABORATOR == \'true\' && $CIRRUS_TAG == "" && ($CIRRUS_BRANCH == $CIRRUS_DEFAULT_BRANCH || $CIRRUS_BRANCH =~ "branch-.*")',
            'maven_cache': maven_cache_conf(),
            'depends_on': ['build'],
            'eks_container': eks_container_conf_factory(),
            'env': {
                'WS_APIKEY': 'VAULT[development/kv/data/mend data.apikey]',
                'JDK_VERSION': '11'
            },
            'whitesource_script': [
                'source cirrus-env QA',
                'source set_maven_build_version $BUILD_NUMBER',
                'mvn clean install -DskipTests',
                'source ws_scan.sh'
            ],
            'cleanup_before_cache_script': 'cleanup_maven_repository',
            'allow_failures': 'true',
            'always': {
                'ws_artifacts': {'path': 'whitesource/**/*'}
            }
        }
    }


def qa_task_conf():
    return {"qa_task":
        {
            'skip': "changesIncludeOnly('docs/**/*', 'spec/**/*', 'README.md')",
            'only_if': '$CIRRUS_USER_COLLABORATOR == \'true\' && $CIRRUS_TAG == "" && $CIRRUS_BUILD_SOURCE != "cron" && ($CIRRUS_PR != "" || $CIRRUS_BRANCH == $CIRRUS_DEFAULT_BRANCH || $CIRRUS_BRANCH =~ "branch-.*" || $CIRRUS_BRANCH =~ "dogfood-on-.*")',
            'maven_cache': {'folder': '${CIRRUS_WORKING_DIR}/.m2/repository',
                            'fingerprint_script': [
                                "find . -name pom.xml -not -path './its/*' -exec cat {} \\+"]},
            'depends_on': ['build'],
            'eks_container': eks_container_conf_factory(memory='8G'),
            'env': {
                'ARTIFACTORY_API_KEY': 'VAULT[development/artifactory/token/${CIRRUS_REPO_OWNER}-${CIRRUS_REPO_NAME}-private-reader access_token]',
                'GITHUB_TOKEN': 'VAULT[development/github/token/licenses-ro token]',
                'MAVEN_OPTS': '-Xmx4G'
            },
            'matrix': [
                {
                    'env': {
                        'SQ_VERSION': 'SonarCloud', 'JDK_VERSION': '17',
                        'CATEGORY': '-Dgroups=SonarCloud',
                        'SONARCLOUD_IT_PASSWORD': 'VAULT[development/team/sonarlint/kv/data/sonarcloud-it data.password]',
                        'QA_CATEGORY': 'SonarCloud'
                    }
                },
                {
                    'env': {
                        'SQ_VERSION': 'DEV', 'JDK_VERSION': '17',
                        'CATEGORY': '-DexcludedGroups=SonarCloud',
                        'QA_CATEGORY': 'SQDogfood'
                    }
                },
                {
                    'env': {
                        'SQ_VERSION': 'LATEST_RELEASE', 'JDK_VERSION': '17',
                        'CATEGORY': '-DexcludedGroups=SonarCloud', 'QA_CATEGORY': 'SQLatest'
                    }
                },
                {
                    'env': {
                        'SQ_VERSION': 'LATEST_RELEASE[8.9]', 'JDK_VERSION': '11',
                        'CATEGORY': '-DexcludedGroups=SonarCloud', 'QA_CATEGORY': 'SQLts89'
                    }
                },
                {
                    'env': {
                        'SQ_VERSION': 'LATEST_RELEASE[7.9]', 'JDK_VERSION': '11',
                        'CATEGORY': '-DexcludedGroups=SonarCloud', 'QA_CATEGORY': 'SQLts79'
                    }
                }
            ],
            'qa_script': 'source cirrus-env QA\nsource set_maven_build_version $BUILD_NUMBER\nif [[ ${CIRRUS_PR:-} != "" || $CIRRUS_BRANCH == $CIRRUS_DEFAULT_BRANCH || $CIRRUS_BRANCH =~ "branch-".* || $CIRRUS_BRANCH =~ "dogfood-on-".* ]]; then\n  mvn -f its/pom.xml -Dsonar.runtimeVersion=${SQ_VERSION} ${CATEGORY} -B -e -V verify surefire-report:report\nelse\n  mvn clean install -DskipTests\n  mvn -rf its -Pits -Dsonar.runtimeVersion=${SQ_VERSION} ${CATEGORY} -B -e -V verify surefire-report:report\nfi\n',
            'cleanup_before_cache_script': ['cleanup_maven_repository'],
            'on_failure': {
                'junit_artifacts': {
                    'path': '**/target/surefire-reports/TEST-*.xml',
                    'format': 'junit'
                }
            }
        }
    }


def promote_task_conf():
    return {
        "promote_task": {
            'skip': "changesIncludeOnly('docs/**/*', 'spec/**/*', 'README.md')",
            'only_if': '$CIRRUS_USER_COLLABORATOR == \'true\' && $CIRRUS_TAG == "" && $CIRRUS_BUILD_SOURCE != "cron" && ($CIRRUS_PR != "" || $CIRRUS_BRANCH == $CIRRUS_DEFAULT_BRANCH || $CIRRUS_BRANCH =~ "branch-.*" || $CIRRUS_BRANCH =~ "dogfood-on-.*")',
            'maven_cache': maven_cache_conf(),
            'depends_on': [
                'build',
                'test_linux',
                'test_windows',
                'mend_scan',
                'qa'
            ],
            'eks_container': eks_container_conf_factory(memory="1G"),
            'env': {
                'JDK_VERSION': '11',
                'GITHUB_TOKEN': 'VAULT[development/github/token/${CIRRUS_REPO_OWNER}-${CIRRUS_REPO_NAME}-promotion token]',
                'ARTIFACTS': 'org.sonarsource.sonarlint.core:sonarlint-core:jar'
            },
            'script': 'cirrus_promote_maven',
            'cleanup_before_cache_script': 'cleanup_maven_repository'
        }
    }


def main(ctx):
    conf = dict()
    re_builtins_conf = load_features(ctx)
    merge_conf_into(conf, re_builtins_conf)
    merge_conf_into(conf, env_conf())
    merge_conf_into(conf, build_task_conf())
    merge_conf_into(conf, test_linux_task_conf())
    merge_conf_into(conf, test_windows_task_conf())
    merge_conf_into(conf, mend_scan_task_conf())
    merge_conf_into(conf, qa_task_conf())
    merge_conf_into(conf, promote_task_conf())
    return conf
