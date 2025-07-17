/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package mediumtest.sca;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.GetDependencyRiskDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.GetDependencyRiskDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ScaIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sca.DidChangeScaIssuesParams;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SCA_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue.Severity;
import static org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue.Status;
import static org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue.Type;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerScaIssueFixtures.aServerScaIssue;

class ScaIssuesMediumTests {

  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";
  private static final String PROJECT_KEY = "projectKey";

  @SonarLintTest
  void it_should_return_no_sca_issues_if_the_scope_does_not_exist(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .start();

    var scaIssues = listAllScaIssues(backend, CONFIG_SCOPE_ID);

    assertThat(scaIssues).isEmpty();
  }

  @SonarLintTest
  void it_should_return_no_sca_issues_if_the_scope_is_not_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start();

    var scaIssues = listAllScaIssues(backend, CONFIG_SCOPE_ID);

    assertThat(scaIssues).isEmpty();
  }

  @SonarLintTest
  void it_should_return_no_sca_issues_if_the_storage_is_empty(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withSonarQubeConnection(CONNECTION_ID)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var scaIssues = listAllScaIssues(backend, CONFIG_SCOPE_ID);

    assertThat(scaIssues).isEmpty();
  }

  @SonarLintTest
  void it_should_return_the_stored_sca_issues(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .start();
    var scaIssueKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject(PROJECT_KEY,
          project -> project.withMainBranch("main",
            branch -> branch.withScaIssue(aServerScaIssue()
              .withKey(scaIssueKey)
              .withPackageName("com.example.vulnerable")
              .withPackageVersion("2.1.0")
              .withType(Type.VULNERABILITY)
              .withSeverity(Severity.HIGH)))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var scaIssues = listAllScaIssues(backend, CONFIG_SCOPE_ID);

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(scaIssues)
      .extracting(ScaIssueDto::getId)
      .containsOnly(scaIssueKey));
  }

  @SonarLintTest
  void it_should_refresh_sca_issues_when_requested(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY,
        project -> project.withBranch("main",
          branch -> branch
            .withScaIssue(
              new ServerFixture.AbstractServerBuilder.ServerProjectBuilder.ServerScaIssue(scaIssueKey.toString(), "PROHIBITED_LICENSE", "HIGH", "OPEN", "com.example.vulnerable",
                "2.1.0", List.of("CONFIRM")))))
      .start();
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage
          .withGlobalSettings(Map.of("sonar.sca.enabled", "true"))
          .withProject(PROJECT_KEY, project -> project.withMainBranch("main")))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var refreshedScaIssues = refreshAndListAllScaIssues(backend, CONFIG_SCOPE_ID);

    assertThat(refreshedScaIssues)
      .extracting(ScaIssueDto::getId, ScaIssueDto::getType, ScaIssueDto::getSeverity, ScaIssueDto::getStatus, ScaIssueDto::getTransitions, ScaIssueDto::getPackageName,
        ScaIssueDto::getPackageVersion)
      .containsExactly(
        tuple(scaIssueKey, ScaIssueDto.Type.PROHIBITED_LICENSE, ScaIssueDto.Severity.HIGH, ScaIssueDto.Status.OPEN, List.of(ScaIssueDto.Transition.CONFIRM),
          "com.example.vulnerable", "2.1.0"));
  }

  @SonarLintTest
  void it_should_notify_client_when_new_sca_issues_are_added(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var server = harness.newFakeSonarQubeServer()
      .withGlobalSetting("sonar.sca.enabled", "true")
      .withProject(PROJECT_KEY,
        project -> project.withBranch("main",
          branch -> branch
            .withScaIssue(
              new ServerFixture.AbstractServerBuilder.ServerProjectBuilder.ServerScaIssue(scaIssueKey.toString(), "VULNERABILITY", "HIGH", "OPEN", "com.example.vulnerable",
                "2.1.0", List.of("CONFIRM")))))
      .start();
    var client = harness.newFakeClient().build();
    harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION, SCA_SYNCHRONIZATION)
      .start(client);
    client.waitForSynchronization();

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
      var scaChanges = client.getScaIssueChanges();
      assertThat(scaChanges).hasSize(1);
      var change = scaChanges.get(0);
      assertThat(change.getConfigurationScopeId()).isEqualTo(CONFIG_SCOPE_ID);
      assertThat(change.getClosedScaIssueIds()).isEmpty();
      assertThat(change.getAddedScaIssues())
        .hasSize(1)
        .first()
        .satisfies(scaIssue -> {
          assertThat(scaIssue.getId()).isEqualTo(scaIssueKey);
          assertThat(scaIssue.getPackageName()).isEqualTo("com.example.vulnerable");
          assertThat(scaIssue.getPackageVersion()).isEqualTo("2.1.0");
          assertThat(scaIssue.getType()).isEqualTo(ScaIssueDto.Type.VULNERABILITY);
          assertThat(scaIssue.getSeverity()).isEqualTo(ScaIssueDto.Severity.HIGH);
        });
      assertThat(change.getUpdatedScaIssues()).isEmpty();
    });
  }

  @SonarLintTest
  void it_should_notify_client_when_sca_issues_are_removed(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var server = harness.newFakeSonarQubeServer()
      .withGlobalSetting("sonar.sca.enabled", "true")
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject(PROJECT_KEY,
          project -> project.withMainBranch("main",
            branch -> branch.withScaIssue(aServerScaIssue()
              .withKey(scaIssueKey)
              .withPackageName("com.example.vulnerable")
              .withPackageVersion("2.1.0")
              .withType(Type.VULNERABILITY)
              .withSeverity(Severity.HIGH)))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION, SCA_SYNCHRONIZATION)
      .start(client);
    client.waitForSynchronization();

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
      var scaChanges = client.getScaIssueChanges();
      assertThat(scaChanges)
        .extracting(DidChangeScaIssuesParams::getConfigurationScopeId, DidChangeScaIssuesParams::getClosedScaIssueIds, DidChangeScaIssuesParams::getAddedScaIssues,
          DidChangeScaIssuesParams::getUpdatedScaIssues)
        .containsExactly(tuple(CONFIG_SCOPE_ID, Set.of(scaIssueKey), List.of(), List.of()));
    });
  }

  @SonarLintTest
  void it_should_notify_client_when_sca_issues_are_updated(SonarLintTestHarness harness) {
    var scaIssueKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var server = harness.newFakeSonarQubeServer()
      .withGlobalSetting("sonar.sca.enabled", "true")
      .withProject(PROJECT_KEY,
        project -> project.withBranch("main",
          branch -> branch
            .withScaIssue(
              new ServerFixture.AbstractServerBuilder.ServerProjectBuilder.ServerScaIssue(scaIssueKey.toString(), "VULNERABILITY", "LOW", "ACCEPT", "com.example.vulnerable",
                "2.1.0", List.of("REOPEN")))))
      .start();
    var client = harness.newFakeClient().build();
    harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject(PROJECT_KEY,
          project -> project.withMainBranch("main",
            branch -> branch.withScaIssue(aServerScaIssue()
              .withKey(scaIssueKey)
              .withPackageName("com.example.vulnerable2")
              .withPackageVersion("0.1.2")
              .withType(Type.PROHIBITED_LICENSE)
              .withSeverity(Severity.HIGH)
              .withStatus(Status.OPEN)
              .withTransitions(List.of(ServerScaIssue.Transition.REOPEN))))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION, SCA_SYNCHRONIZATION)
      .start(client);
    client.waitForSynchronization();

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
      var scaChanges = client.getScaIssueChanges();
      assertThat(scaChanges).hasSize(1);
      var change = scaChanges.get(0);
      assertThat(change.getConfigurationScopeId()).isEqualTo(CONFIG_SCOPE_ID);
      assertThat(change.getClosedScaIssueIds()).isEmpty();
      assertThat(change.getAddedScaIssues()).isEmpty();
      assertThat(change.getUpdatedScaIssues())
        .extracting(ScaIssueDto::getId, ScaIssueDto::getType, ScaIssueDto::getSeverity, ScaIssueDto::getStatus, ScaIssueDto::getTransitions, ScaIssueDto::getPackageName,
          ScaIssueDto::getPackageVersion)
        .containsExactly(
          tuple(scaIssueKey, ScaIssueDto.Type.VULNERABILITY, ScaIssueDto.Severity.LOW, ScaIssueDto.Status.ACCEPT, List.of(ScaIssueDto.Transition.REOPEN), "com.example.vulnerable",
            "2.1.0"));
    });
  }

  @SonarLintTest
  void it_should_throw_exception_if_scope_does_not_exist(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    var throwable = catchThrowable(() -> getDependencyRiskDetails(backend, CONFIG_SCOPE_ID, "test-dependency-key"));

    assertThat(throwable)
      .isInstanceOf(CompletionException.class);
    assertThat(throwable.getCause())
      .isInstanceOf(ResponseErrorException.class);
    var responseErrorException = (ResponseErrorException) throwable.getCause();
    assertThat(responseErrorException.getResponseError().getCode()).isEqualTo(SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_FOUND);
    assertThat(responseErrorException.getResponseError().getMessage()).contains("The provided configuration scope does not exist: " + CONFIG_SCOPE_ID);
  }

  @SonarLintTest
  void it_should_throw_exception_if_scope_is_not_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start();

    var throwable = catchThrowable(() -> getDependencyRiskDetails(backend, CONFIG_SCOPE_ID, "test-dependency-key"));

    assertThat(throwable)
      .isInstanceOf(CompletionException.class)
      .hasCauseInstanceOf(ResponseErrorException.class);
    var responseErrorException = (ResponseErrorException) throwable.getCause();
    assertThat(responseErrorException.getResponseError().getCode()).isEqualTo(SonarLintRpcErrorCode.CONFIG_SCOPE_NOT_BOUND);
    assertThat(responseErrorException.getResponseError().getMessage())
      .contains("The provided configuration scope is not bound to a SonarQube/SonarCloud project: " + CONFIG_SCOPE_ID);
  }

  @SonarLintTest
  void it_should_return_dependency_risk_details_with_description_and_affected_packages(SonarLintTestHarness harness) {
    var dependencyRiskKey = "CVE-2020-36518";
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .start();

    server.getMockServer().stubFor(
      get(urlEqualTo("/api/v2/sca/issues-releases/" + dependencyRiskKey))
        .willReturn(aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody("""
            {
              "key": "589a534f-53e0-4eca-9987-b91bb42146d6",
              "severity": "BLOCKER",
              "release": {
                "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
                "version": "9.0.70"
              },
              "type": "VULNERABILITY",
              "vulnerability": {
                "vulnerabilityId": "CVE-2023-44487",
                "description": "jackson-databind before 2.13.0 allows a Java StackOverflow exception and denial of service via a large depth of nested objects."
              },
              "affectedPackages": [
                {
                  "purl": "pkg:maven/com.fasterxml.jackson.core/jackson-databind",
                  "recommendation": "upgrade",
                  "recommendationDetails": {
                    "impactScore": 5,
                    "impactDescription": "It is difficult to estimate how commonly untyped deserialization is used but it is not the most common usage style.",
                    "realIssue": true,
                    "falsePositiveReason": "",
                    "includesDev": false,
                    "specificMethodsAffected": false,
                    "specificMethodsDescription": "",
                    "otherConditions": true,
                    "otherConditionsDescription": "Jackson project issues explains details, but essentially this only affects usage where target type is java.lang.Object.",
                    "workaroundAvailable": false,
                    "workaroundDescription": "",
                    "visibility": "external"
                  }
                }
              ]
            }
            """)));

    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = getDependencyRiskDetails(backend, CONFIG_SCOPE_ID, dependencyRiskKey);

    assertThat(response).isNotNull();
    assertThat(response.getDescription())
      .isEqualTo("jackson-databind before 2.13.0 allows a Java StackOverflow exception and denial of service via a large depth of nested objects.");
    assertThat(response.getAffectedPackages())
      .hasSize(1)
      .first()
      .satisfies(pkg -> {
        assertThat(pkg.getPurl()).isEqualTo("pkg:maven/com.fasterxml.jackson.core/jackson-databind");
        assertThat(pkg.getRecommendation()).isEqualTo("upgrade");
        assertThat(pkg.getImpactScore()).isEqualTo(5);
        assertThat(pkg.getImpactDescription()).isEqualTo("It is difficult to estimate how commonly untyped deserialization is used but it is not the most common usage style.");
        assertThat(pkg.isRealIssue()).isTrue();
        assertThat(pkg.getFalsePositiveReason()).isEmpty();
        assertThat(pkg.isIncludesDev()).isFalse();
        assertThat(pkg.isSpecificMethodsAffected()).isFalse();
        assertThat(pkg.getSpecificMethodsDescription()).isEmpty();
        assertThat(pkg.isOtherConditions()).isTrue();
        assertThat(pkg.getOtherConditionsDescription())
          .isEqualTo("Jackson project issues explains details, but essentially this only affects usage where target type is java.lang.Object.");
        assertThat(pkg.isWorkaroundAvailable()).isFalse();
        assertThat(pkg.getWorkaroundDescription()).isEmpty();
        assertThat(pkg.getVisibility()).isEqualTo("external");
      });
  }

  @SonarLintTest
  void it_should_handle_multiple_affected_packages_with_different_recommendations(SonarLintTestHarness harness) {
    var dependencyRiskKey = "SNYK-JAVA-ORGAPACHECOMMONS-72465";
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .start();

    server.getMockServer().stubFor(
      get(urlEqualTo("/api/v2/sca/issues-releases/" + dependencyRiskKey))
        .willReturn(aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody("""
            {
              "key": "589a534f-53e0-4eca-9987-b91bb42146d6",
              "severity": "BLOCKER",
              "release": {
                "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
                "version": "9.0.70"
              },
              "type": "VULNERABILITY",
              "vulnerability": {
                "vulnerabilityId": "CVE-2023-44487",
                "description": "Deserialization of untrusted data vulnerability in Apache Commons Collections."
              },
              "affectedPackages": [
                {
                  "purl": "pkg:maven/org.apache.commons/commons-collections4",
                  "recommendation": "upgrade",
                  "recommendationDetails": {
                    "impactScore": 9,
                    "impactDescription": "High impact vulnerability affecting object deserialization.",
                    "realIssue": true,
                    "falsePositiveReason": "",
                    "includesDev": true,
                    "specificMethodsAffected": true,
                    "specificMethodsDescription": "Affects readObject and related deserialization methods.",
                    "otherConditions": false,
                    "otherConditionsDescription": "",
                    "workaroundAvailable": true,
                    "workaroundDescription": "Disable unsafe deserialization or use custom ObjectInputStream.",
                    "visibility": "public"
                  }
                },
                {
                  "purl": "pkg:maven/commons-collections/commons-collections",
                  "recommendation": "remove",
                  "recommendationDetails": {
                    "impactScore": 7,
                    "impactDescription": "Legacy version with known security issues.",
                    "realIssue": true,
                    "falsePositiveReason": "",
                    "includesDev": false,
                    "specificMethodsAffected": false,
                    "specificMethodsDescription": "",
                    "otherConditions": true,
                    "otherConditionsDescription": "Only affects applications using unsafe serialization practices.",
                    "workaroundAvailable": false,
                    "workaroundDescription": "",
                    "visibility": "internal"
                  }
                }
              ]
            }
            """)));

    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = getDependencyRiskDetails(backend, CONFIG_SCOPE_ID, dependencyRiskKey);

    assertThat(response).isNotNull();
    assertThat(response.getDescription()).isEqualTo("Deserialization of untrusted data vulnerability in Apache Commons Collections.");
    assertThat(response.getAffectedPackages())
      .hasSize(2)
      .extracting("purl", "recommendation", "impactScore", "visibility")
      .containsExactly(
        tuple("pkg:maven/org.apache.commons/commons-collections4", "upgrade", 9, "public"),
        tuple("pkg:maven/commons-collections/commons-collections", "remove", 7, "internal"));

    var firstPackage = response.getAffectedPackages().get(0);
    assertThat(firstPackage.isIncludesDev()).isTrue();
    assertThat(firstPackage.isSpecificMethodsAffected()).isTrue();
    assertThat(firstPackage.getSpecificMethodsDescription()).isEqualTo("Affects readObject and related deserialization methods.");
    assertThat(firstPackage.isWorkaroundAvailable()).isTrue();
    assertThat(firstPackage.getWorkaroundDescription()).isEqualTo("Disable unsafe deserialization or use custom ObjectInputStream.");

    var secondPackage = response.getAffectedPackages().get(1);
    assertThat(secondPackage.isIncludesDev()).isFalse();
    assertThat(secondPackage.isSpecificMethodsAffected()).isFalse();
    assertThat(secondPackage.isOtherConditions()).isTrue();
    assertThat(secondPackage.getOtherConditionsDescription()).isEqualTo("Only affects applications using unsafe serialization practices.");
  }

  @SonarLintTest
  void it_should_handle_dependency_risk_details_with_false_positive_recommendation(SonarLintTestHarness harness) {
    var dependencyRiskKey = "FALSE-POSITIVE-TEST";
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .start();

    server.getMockServer().stubFor(
      get(urlEqualTo("/api/v2/sca/issues-releases/" + dependencyRiskKey))
        .willReturn(aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody("""
            {
              "key": "589a534f-53e0-4eca-9987-b91bb42146d6",
              "severity": "BLOCKER",
              "release": {
                "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
                "version": "9.0.70"
              },
              "type": "VULNERABILITY",
              "vulnerability": {
                "vulnerabilityId": "CVE-2023-44487",
                "description": "Potential vulnerability that may be a false positive."
              },
              "affectedPackages": [
                {
                  "purl": "pkg:npm/example-package",
                  "recommendation": "review",
                  "recommendationDetails": {
                    "impactScore": 3,
                    "impactDescription": "Low impact due to specific usage context.",
                    "realIssue": false,
                    "falsePositiveReason": "This vulnerability only affects server-side usage, but this package is used client-side only.",
                    "includesDev": true,
                    "specificMethodsAffected": false,
                    "specificMethodsDescription": "",
                    "otherConditions": false,
                    "otherConditionsDescription": "",
                    "workaroundAvailable": false,
                    "workaroundDescription": "",
                    "visibility": "private"
                  }
                }
              ]
            }
            """)));

    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = getDependencyRiskDetails(backend, CONFIG_SCOPE_ID, dependencyRiskKey);

    assertThat(response).isNotNull();
    assertThat(response.getDescription()).isEqualTo("Potential vulnerability that may be a false positive.");
    assertThat(response.getAffectedPackages())
      .hasSize(1)
      .first()
      .satisfies(pkg -> {
        assertThat(pkg.getPurl()).isEqualTo("pkg:npm/example-package");
        assertThat(pkg.getRecommendation()).isEqualTo("review");
        assertThat(pkg.getImpactScore()).isEqualTo(3);
        assertThat(pkg.isRealIssue()).isFalse();
        assertThat(pkg.getFalsePositiveReason()).isEqualTo("This vulnerability only affects server-side usage, but this package is used client-side only.");
        assertThat(pkg.getVisibility()).isEqualTo("private");
      });
  }

  @SonarLintTest
  void it_should_handle_dependency_risk_details_with_minimal_data(SonarLintTestHarness harness) {
    var dependencyRiskKey = "MINIMAL-DATA-TEST";
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .start();

    server.getMockServer().stubFor(
      get(urlEqualTo("/api/v2/sca/issues-releases/" + dependencyRiskKey))
        .willReturn(aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody("""
            {
              "key": "589a534f-53e0-4eca-9987-b91bb42146d6",
              "severity": "BLOCKER",
              "release": {
                "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
                "version": "9.0.70"
              },
              "type": "VULNERABILITY",
              "vulnerability": {
                "vulnerabilityId": "CVE-2023-44487",
                "description": "Minimal vulnerability description."
              },
              "affectedPackages": [
                {
                  "purl": "pkg:maven/minimal-package",
                  "recommendation": "monitor",
                  "recommendationDetails": {
                    "impactScore": 0,
                    "impactDescription": "",
                    "realIssue": true,
                    "falsePositiveReason": "",
                    "includesDev": false,
                    "specificMethodsAffected": false,
                    "specificMethodsDescription": "",
                    "otherConditions": false,
                    "otherConditionsDescription": "",
                    "workaroundAvailable": false,
                    "workaroundDescription": "",
                    "visibility": ""
                  }
                }
              ]
            }
            """)));

    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = getDependencyRiskDetails(backend, CONFIG_SCOPE_ID, dependencyRiskKey);

    assertThat(response).isNotNull();
    assertThat(response.getDescription()).isEqualTo("Minimal vulnerability description.");
    assertThat(response.getAffectedPackages())
      .hasSize(1)
      .first()
      .satisfies(pkg -> {
        assertThat(pkg.getPurl()).isEqualTo("pkg:maven/minimal-package");
        assertThat(pkg.getRecommendation()).isEqualTo("monitor");
        assertThat(pkg.getImpactScore()).isZero();
        assertThat(pkg.getImpactDescription()).isEmpty();
        assertThat(pkg.isRealIssue()).isTrue();
        assertThat(pkg.getFalsePositiveReason()).isEmpty();
        assertThat(pkg.isIncludesDev()).isFalse();
        assertThat(pkg.isSpecificMethodsAffected()).isFalse();
        assertThat(pkg.getSpecificMethodsDescription()).isEmpty();
        assertThat(pkg.isOtherConditions()).isFalse();
        assertThat(pkg.getOtherConditionsDescription()).isEmpty();
        assertThat(pkg.isWorkaroundAvailable()).isFalse();
        assertThat(pkg.getWorkaroundDescription()).isEmpty();
        assertThat(pkg.getVisibility()).isEmpty();
      });
  }

  @SonarLintTest
  void it_should_handle_dependency_risk_details_with_empty_affected_packages(SonarLintTestHarness harness) {
    var dependencyRiskKey = "EMPTY-PACKAGES-TEST";
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .start();

    server.getMockServer().stubFor(
      get(urlEqualTo("/api/v2/sca/issues-releases/" + dependencyRiskKey))
        .willReturn(aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody("""
            {
              "key": "589a534f-53e0-4eca-9987-b91bb42146d6",
              "severity": "BLOCKER",
              "release": {
                "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
                "version": "9.0.70"
              },
              "type": "VULNERABILITY",
              "vulnerability": {
                "vulnerabilityId": "CVE-2023-44487",
                "description": "Risk with no affected packages."
              },
              "affectedPackages": []
            }
            """)));

    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = getDependencyRiskDetails(backend, CONFIG_SCOPE_ID, dependencyRiskKey);

    assertThat(response).isNotNull();
    assertThat(response.getDescription()).isEqualTo("Risk with no affected packages.");
    assertThat(response.getAffectedPackages()).isEmpty();
  }

  private List<ScaIssueDto> listAllScaIssues(SonarLintTestRpcServer backend, String configScopeId) {
    return backend.getScaIssueTrackingService().listAll(new ListAllParams(configScopeId)).join().getScaIssues();
  }

  private List<ScaIssueDto> refreshAndListAllScaIssues(SonarLintTestRpcServer backend, String configScopeId) {
    return backend.getScaIssueTrackingService().listAll(new ListAllParams(configScopeId, true)).join().getScaIssues();
  }

  private GetDependencyRiskDetailsResponse getDependencyRiskDetails(SonarLintTestRpcServer backend, String configScopeId, String dependencyRiskKey) {
    return backend.getScaService().getDependencyRiskDetails(new GetDependencyRiskDetailsParams(configScopeId, dependencyRiskKey)).join();
  }
}
