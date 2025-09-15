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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.assertj.core.groups.Tuple;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.CheckDependencyRiskSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.CheckDependencyRiskSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.GetDependencyRiskDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.GetDependencyRiskDetailsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sca.DidChangeDependencyRisksParams;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
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
import static org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk.Severity;
import static org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk.Status;
import static org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk.Type;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerDependencyRiskFixtures.aServerDependencyRisk;

class DependencyRisksMediumTests {

  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";
  private static final String PROJECT_KEY = "projectKey";

  @SonarLintTest
  void it_should_return_no_dependency_risks_if_the_scope_does_not_exist(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .start();

    var dependencyRisks = listAllDependencyRisks(backend, CONFIG_SCOPE_ID);

    assertThat(dependencyRisks).isEmpty();
  }

  @SonarLintTest
  void it_should_return_no_dependency_risks_if_the_scope_is_not_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start();

    var dependencyRisks = listAllDependencyRisks(backend, CONFIG_SCOPE_ID);

    assertThat(dependencyRisks).isEmpty();
  }

  @SonarLintTest
  void it_should_return_no_dependency_risks_if_the_storage_is_empty(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withSonarQubeConnection(CONNECTION_ID)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var dependencyRisks = listAllDependencyRisks(backend, CONFIG_SCOPE_ID);

    assertThat(dependencyRisks).isEmpty();
  }

  @SonarLintTest
  void it_should_return_the_stored_dependency_risks(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .start();
    var dependencyRiskKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject(PROJECT_KEY,
          project -> project.withMainBranch("main",
            branch -> branch.withDependencyRisk(aServerDependencyRisk()
              .withKey(dependencyRiskKey)
              .withPackageName("com.example.vulnerable")
              .withPackageVersion("2.1.0")
              .withVulnerabilityId("CVE-1234")
              .withCvssScore("7.5")
              .withType(Type.VULNERABILITY)
              .withSeverity(Severity.HIGH)))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var dependencyRisks = listAllDependencyRisks(backend, CONFIG_SCOPE_ID);

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(dependencyRisks)
      .extracting(DependencyRiskDto::getId)
      .containsOnly(dependencyRiskKey));
  }

  @SonarLintTest
  void it_should_return_the_stored_fixed_dependency_risks(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .start();
    var dependencyRiskKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject(PROJECT_KEY,
          project -> project.withMainBranch("main",
            branch -> branch.withDependencyRisk(aServerDependencyRisk()
              .withKey(dependencyRiskKey)
              .withStatus(Status.FIXED)))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var dependencyRisks = listAllDependencyRisks(backend, CONFIG_SCOPE_ID);

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(dependencyRisks)
      .extracting(DependencyRiskDto::getId, DependencyRiskDto::getStatus)
      .containsOnly(Tuple.tuple(dependencyRiskKey, DependencyRiskDto.Status.FIXED)));
  }

  @SonarLintTest
  void it_should_refresh_dependency_risks_when_requested(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var server = harness.newFakeSonarQubeServer()
      .withProject(PROJECT_KEY,
        project -> project.withBranch("main",
          branch -> branch
            .withDependencyRisk(
              new ServerFixture.AbstractServerBuilder.ServerProjectBuilder.ServerDependencyRisk(dependencyRiskKey.toString(), "PROHIBITED_LICENSE",
                "HIGH", "MAINTAINABILITY", "OPEN", "com.example.vulnerable", "2.1.0",
                null, null, List.of("CONFIRM")))))
      .start();
    var backend = harness.newBackend()
      .withBackendCapability(SCA_SYNCHRONIZATION)
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage
          .withServerFeature(Feature.SCA)
          .withProject(PROJECT_KEY, project -> project.withMainBranch("main")))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var refreshedDependencyRisks = refreshAndListAllDependencyRisks(backend, CONFIG_SCOPE_ID);

    assertThat(refreshedDependencyRisks)
      .extracting(DependencyRiskDto::getId, DependencyRiskDto::getType, DependencyRiskDto::getSeverity, DependencyRiskDto::getQuality, DependencyRiskDto::getStatus,
        DependencyRiskDto::getTransitions, DependencyRiskDto::getPackageName, DependencyRiskDto::getPackageVersion)
      .containsExactly(
        tuple(dependencyRiskKey, DependencyRiskDto.Type.PROHIBITED_LICENSE, DependencyRiskDto.Severity.HIGH, DependencyRiskDto.SoftwareQuality.MAINTAINABILITY,
          DependencyRiskDto.Status.OPEN, List.of(DependencyRiskDto.Transition.CONFIRM), "com.example.vulnerable", "2.1.0"));
  }

  @SonarLintTest
  void it_should_notify_client_when_new_dependency_risks_are_added(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var server = harness.newFakeSonarQubeServer()
      .withFeature("sca")
      .withProject(PROJECT_KEY,
        project -> project.withBranch("main",
          branch -> branch
            .withDependencyRisk(
              new ServerFixture.AbstractServerBuilder.ServerProjectBuilder.ServerDependencyRisk(dependencyRiskKey.toString(), "VULNERABILITY", "HIGH",
                "SECURITY", "OPEN", "com.example.vulnerable", "2.1.0",
                "CVE-1234", "7.5", List.of("CONFIRM")))))
      .start();
    var client = harness.newFakeClient().build();
    harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION, SCA_SYNCHRONIZATION)
      .start(client);
    client.waitForSynchronization();

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
      var changes = client.getDependencyRiskChanges();
      assertThat(changes).hasSize(1);
      var change = changes.get(0);
      assertThat(change.getConfigurationScopeId()).isEqualTo(CONFIG_SCOPE_ID);
      assertThat(change.getClosedDependencyRiskIds()).isEmpty();
      assertThat(change.getAddedDependencyRisks())
        .hasSize(1)
        .first()
        .satisfies(dependencyRisk -> {
          assertThat(dependencyRisk.getId()).isEqualTo(dependencyRiskKey);
          assertThat(dependencyRisk.getPackageName()).isEqualTo("com.example.vulnerable");
          assertThat(dependencyRisk.getPackageVersion()).isEqualTo("2.1.0");
          assertThat(dependencyRisk.getVulnerabilityId()).isEqualTo("CVE-1234");
          assertThat(dependencyRisk.getCvssScore()).isEqualTo("7.5");
          assertThat(dependencyRisk.getType()).isEqualTo(DependencyRiskDto.Type.VULNERABILITY);
          assertThat(dependencyRisk.getSeverity()).isEqualTo(DependencyRiskDto.Severity.HIGH);
          assertThat(dependencyRisk.getQuality()).isEqualTo(DependencyRiskDto.SoftwareQuality.SECURITY);
        });
      assertThat(change.getUpdatedDependencyRisks()).isEmpty();
    });
  }

  @SonarLintTest
  void it_should_notify_client_when_dependency_risks_are_removed(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var server = harness.newFakeSonarQubeServer()
      .withFeature("sca")
      .withProject(PROJECT_KEY, project -> project.withBranch("main"))
      .start();
    var client = harness.newFakeClient().build();
    harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject(PROJECT_KEY,
          project -> project.withMainBranch("main",
            branch -> branch.withDependencyRisk(aServerDependencyRisk()
              .withKey(dependencyRiskKey)
              .withPackageName("com.example.vulnerable")
              .withPackageVersion("2.1.0")
              .withVulnerabilityId("CVE-1234")
              .withCvssScore("7.5")
              .withType(Type.VULNERABILITY)
              .withSeverity(Severity.HIGH)))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION, SCA_SYNCHRONIZATION)
      .start(client);
    client.waitForSynchronization();

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
      var changes = client.getDependencyRiskChanges();
      assertThat(changes)
        .extracting(DidChangeDependencyRisksParams::getConfigurationScopeId, DidChangeDependencyRisksParams::getClosedDependencyRiskIds, DidChangeDependencyRisksParams::getAddedDependencyRisks,
          DidChangeDependencyRisksParams::getUpdatedDependencyRisks)
        .containsExactly(tuple(CONFIG_SCOPE_ID, Set.of(dependencyRiskKey), List.of(), List.of()));
    });
  }

  @SonarLintTest
  void it_should_notify_client_when_dependency_risks_are_updated(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    var server = harness.newFakeSonarQubeServer()
      .withFeature("sca")
      .withProject(PROJECT_KEY,
        project -> project.withBranch("main",
          branch -> branch
            .withDependencyRisk(
              new ServerFixture.AbstractServerBuilder.ServerProjectBuilder.ServerDependencyRisk(dependencyRiskKey.toString(), "VULNERABILITY", "LOW",
                "RELIABILITY", "ACCEPT", "com.example.vulnerable", "2.1.0", "CVE-1234",
                "7.5", List.of("REOPEN")))))
      .start();
    var client = harness.newFakeClient().build();
    harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage.withProject(PROJECT_KEY,
          project -> project.withMainBranch("main",
            branch -> branch.withDependencyRisk(aServerDependencyRisk()
              .withKey(dependencyRiskKey)
              .withPackageName("com.example.vulnerable2")
              .withPackageVersion("0.1.2")
              .withType(Type.PROHIBITED_LICENSE)
              .withSeverity(Severity.HIGH)
              .withQuality(ServerDependencyRisk.SoftwareQuality.RELIABILITY)
              .withStatus(Status.OPEN)
              .withTransitions(List.of(ServerDependencyRisk.Transition.REOPEN))))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .withBackendCapability(FULL_SYNCHRONIZATION, SCA_SYNCHRONIZATION)
      .start(client);
    client.waitForSynchronization();

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
      var changes = client.getDependencyRiskChanges();
      assertThat(changes).hasSize(1);
      var change = changes.get(0);
      assertThat(change.getConfigurationScopeId()).isEqualTo(CONFIG_SCOPE_ID);
      assertThat(change.getClosedDependencyRiskIds()).isEmpty();
      assertThat(change.getAddedDependencyRisks()).isEmpty();
      assertThat(change.getUpdatedDependencyRisks())
        .extracting(DependencyRiskDto::getId, DependencyRiskDto::getType, DependencyRiskDto::getSeverity, DependencyRiskDto::getStatus,
          DependencyRiskDto::getTransitions, DependencyRiskDto::getPackageName, DependencyRiskDto::getPackageVersion,
          DependencyRiskDto::getVulnerabilityId, DependencyRiskDto::getCvssScore)
        .containsExactly(
          tuple(dependencyRiskKey, DependencyRiskDto.Type.VULNERABILITY, DependencyRiskDto.Severity.LOW, DependencyRiskDto.Status.ACCEPT, List.of(DependencyRiskDto.Transition.REOPEN), "com.example.vulnerable",
            "2.1.0", "CVE-1234", "7.5"));
    });
  }

  @SonarLintTest
  void it_should_throw_exception_if_scope_does_not_exist(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();

    var throwable = catchThrowable(() -> getDependencyRiskDetails(backend, CONFIG_SCOPE_ID, UUID.randomUUID()));

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

    var throwable = catchThrowable(() -> getDependencyRiskDetails(backend, CONFIG_SCOPE_ID, UUID.randomUUID()));

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
    var dependencyRiskKey = UUID.randomUUID();
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
              "quality": "SECURITY",
              "release": {
                "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
                "version": "9.0.70"
              },
              "type": "VULNERABILITY",
              "vulnerability": {
                "vulnerabilityId": "CVE-2023-44487",
                "description": "jackson-databind before 2.13.0 allows a Java StackOverflow exception and denial of service via a large depth of nested objects.",
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
        var recommendationDetails = pkg.getRecommendationDetails();
        assertThat(recommendationDetails).isNotNull();
        assertThat(recommendationDetails.getImpactScore()).isEqualTo(5);
        assertThat(recommendationDetails.getImpactDescription()).isEqualTo("It is difficult to estimate how commonly untyped deserialization is used but it is not the most common usage style.");
        assertThat(recommendationDetails.isRealIssue()).isTrue();
        assertThat(recommendationDetails.getFalsePositiveReason()).isEmpty();
        assertThat(recommendationDetails.isIncludesDev()).isFalse();
        assertThat(recommendationDetails.isSpecificMethodsAffected()).isFalse();
        assertThat(recommendationDetails.getSpecificMethodsDescription()).isEmpty();
        assertThat(recommendationDetails.isOtherConditions()).isTrue();
        assertThat(recommendationDetails.getOtherConditionsDescription())
          .isEqualTo("Jackson project issues explains details, but essentially this only affects usage where target type is java.lang.Object.");
        assertThat(recommendationDetails.isWorkaroundAvailable()).isFalse();
        assertThat(recommendationDetails.getWorkaroundDescription()).isEmpty();
        assertThat(recommendationDetails.getVisibility()).isEqualTo("external");
      });
  }

  @SonarLintTest
  void it_should_handle_multiple_affected_packages_with_different_recommendations(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
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
              "quality": "SECURITY",
              "release": {
                "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
                "version": "9.0.70"
              },
              "type": "VULNERABILITY",
              "vulnerability": {
                "vulnerabilityId": "CVE-2023-44487",
                "description": "Deserialization of untrusted data vulnerability in Apache Commons Collections.",
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
            }
            """)));

    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = getDependencyRiskDetails(backend, CONFIG_SCOPE_ID, dependencyRiskKey);

    assertThat(response).isNotNull();
    assertThat(response.getDescription()).isNotNull();
    assertThat(response.getDescription()).isEqualTo("Deserialization of untrusted data vulnerability in Apache Commons Collections.");
    assertThat(response.getAffectedPackages())
      .hasSize(2)
      .extracting("purl", "recommendation", "recommendationDetails.impactScore", "recommendationDetails.visibility")
      .containsExactly(
        tuple("pkg:maven/org.apache.commons/commons-collections4", "upgrade", 9, "public"),
        tuple("pkg:maven/commons-collections/commons-collections", "remove", 7, "internal"));

    var firstPackage = response.getAffectedPackages().get(0);
    var firstRecommendationDetails = firstPackage.getRecommendationDetails();
    assertThat(firstRecommendationDetails).isNotNull();
    assertThat(firstRecommendationDetails.isIncludesDev()).isTrue();
    assertThat(firstRecommendationDetails.isSpecificMethodsAffected()).isTrue();
    assertThat(firstRecommendationDetails.getSpecificMethodsDescription()).isEqualTo("Affects readObject and related deserialization methods.");
    assertThat(firstRecommendationDetails.isWorkaroundAvailable()).isTrue();
    assertThat(firstRecommendationDetails.getWorkaroundDescription()).isEqualTo("Disable unsafe deserialization or use custom ObjectInputStream.");

    var secondPackage = response.getAffectedPackages().get(1);
    var secondRecommendationDetails = secondPackage.getRecommendationDetails();
    assertThat(secondRecommendationDetails).isNotNull();
    assertThat(secondRecommendationDetails.isIncludesDev()).isFalse();
    assertThat(secondRecommendationDetails.isSpecificMethodsAffected()).isFalse();
    assertThat(secondRecommendationDetails.isOtherConditions()).isTrue();
    assertThat(secondRecommendationDetails.getOtherConditionsDescription()).isEqualTo("Only affects applications using unsafe serialization practices.");
  }

  @SonarLintTest
  void it_should_handle_dependency_risk_details_with_false_positive_recommendation(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
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
              "quality": "SECURITY",
              "release": {
                "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
                "version": "9.0.70"
              },
              "type": "VULNERABILITY",
              "vulnerability": {
                "vulnerabilityId": "CVE-2023-44487",
                "description": "Potential vulnerability that may be a false positive.",
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
        var recommendationDetails = pkg.getRecommendationDetails();
        assertThat(recommendationDetails).isNotNull();
        assertThat(recommendationDetails.getImpactScore()).isEqualTo(3);
        assertThat(recommendationDetails.isRealIssue()).isFalse();
        assertThat(recommendationDetails.getFalsePositiveReason()).isEqualTo("This vulnerability only affects server-side usage, but this package is used client-side only.");
        assertThat(recommendationDetails.getVisibility()).isEqualTo("private");
      });
  }

  @SonarLintTest
  void it_should_handle_dependency_risk_details_with_minimal_data(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
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
              "quality": "SECURITY",
              "release": {
                "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
                "version": "9.0.70"
              },
              "type": "VULNERABILITY",
              "vulnerability": {
                "vulnerabilityId": "CVE-2023-44487",
                "description": "Minimal vulnerability description.",
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
        var recommendationDetails = pkg.getRecommendationDetails();
        assertThat(recommendationDetails).isNotNull();
        assertThat(recommendationDetails.getImpactScore()).isZero();
        assertThat(recommendationDetails.getImpactDescription()).isEmpty();
        assertThat(recommendationDetails.isRealIssue()).isTrue();
        assertThat(recommendationDetails.getFalsePositiveReason()).isEmpty();
        assertThat(recommendationDetails.isIncludesDev()).isFalse();
        assertThat(recommendationDetails.isSpecificMethodsAffected()).isFalse();
        assertThat(recommendationDetails.getSpecificMethodsDescription()).isEmpty();
        assertThat(recommendationDetails.isOtherConditions()).isFalse();
        assertThat(recommendationDetails.getOtherConditionsDescription()).isEmpty();
        assertThat(recommendationDetails.isWorkaroundAvailable()).isFalse();
        assertThat(recommendationDetails.getWorkaroundDescription()).isEmpty();
        assertThat(recommendationDetails.getVisibility()).isEmpty();
      });
  }@SonarLintTest
  void it_should_handle_dependency_risk_details_with_empty_recommendation_details(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
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
              "quality": "SECURITY",
              "release": {
                "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
                "version": "9.0.70"
              },
              "type": "VULNERABILITY",
              "vulnerability": {
                "vulnerabilityId": "CVE-2023-44487",
                "description": "Minimal vulnerability description.",
                "affectedPackages": [
                  {
                    "purl": "pkg:maven/minimal-package",
                    "recommendation": "monitor",
                    "recommendationDetails": null
                  }
                ]
              }
            }
            """)));

    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = getDependencyRiskDetails(backend, CONFIG_SCOPE_ID, dependencyRiskKey);

    assertThat(response.getAffectedPackages())
      .hasSize(1)
      .first()
      .satisfies(pkg -> {
        assertThat(pkg.getPurl()).isEqualTo("pkg:maven/minimal-package");
        assertThat(pkg.getRecommendation()).isEqualTo("monitor");
        assertThat(pkg.getRecommendationDetails()).isNull();
      });
  }

  @SonarLintTest
  void it_should_handle_dependency_risk_details_with_empty_affected_packages(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
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
              "quality": "SECURITY",
              "release": {
                "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
                "version": "9.0.70"
              },
              "type": "VULNERABILITY",
              "vulnerability": {
                "vulnerabilityId": "CVE-2023-44487",
                "description": "Risk with no affected packages.",
                "affectedPackages": []
              }
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

  @SonarLintTest
  void it_should_handle_dependency_risk_details_for_prohibited_license(SonarLintTestHarness harness) {
    var dependencyRiskKey = UUID.randomUUID();
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
              "quality": "SECURITY",
              "release": {
                "packageName": "org.apache.tomcat.embed:tomcat-embed-core",
                "version": "9.0.70"
              },
              "type": "PROHIBITED_LICENSE",
              "vulnerability": null
            }
            """)));

    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = getDependencyRiskDetails(backend, CONFIG_SCOPE_ID, dependencyRiskKey);

    assertThat(response).isNotNull();
    assertThat(response.getType()).isEqualTo(DependencyRiskDto.Type.PROHIBITED_LICENSE);
    assertThat(response.getDescription()).isNull();
    assertThat(response.getVulnerabilityId()).isNull();
  }

  @SonarLintTest
  void it_should_check_for_supported_sca(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage
          .withServerFeature(Feature.SCA)
          .withServerVersion("2025.4"))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = checkSupported(backend, CONFIG_SCOPE_ID);

    assertThat(response.isSupported()).isTrue();
    assertThat(response.getReason()).isNull();
  }

  @SonarLintTest
  void it_should_not_be_supported_if_old_version(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage
          .withServerFeature(Feature.SCA)
          .withServerVersion("2025.3"))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = checkSupported(backend, CONFIG_SCOPE_ID);

    assertThat(response.isSupported()).isFalse();
    assertThat(response.getReason()).isEqualTo("The connected SonarQube Server version is lower than the minimum supported version 2025.4");
  }

  @SonarLintTest
  void it_should_not_be_supported_if_sca_disabled(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, server,
        storage -> storage
          .withServerVersion("2025.4"))
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, PROJECT_KEY)
      .start();

    var response = checkSupported(backend, CONFIG_SCOPE_ID);

    assertThat(response.isSupported()).isFalse();
    assertThat(response.getReason()).isEqualTo("The connected SonarQube Server does not have Advanced Security enabled (requires Enterprise edition or higher)");
  }

  private List<DependencyRiskDto> listAllDependencyRisks(SonarLintTestRpcServer backend, String configScopeId) {
    return backend.getDependencyRiskService().listAll(new ListAllParams(configScopeId)).join().getDependencyRisks();
  }

  private List<DependencyRiskDto> refreshAndListAllDependencyRisks(SonarLintTestRpcServer backend, String configScopeId) {
    return backend.getDependencyRiskService().listAll(new ListAllParams(configScopeId, true)).join().getDependencyRisks();
  }

  private GetDependencyRiskDetailsResponse getDependencyRiskDetails(SonarLintTestRpcServer backend, String configScopeId, UUID dependencyRiskKey) {
    return backend.getDependencyRiskService().getDependencyRiskDetails(new GetDependencyRiskDetailsParams(configScopeId, dependencyRiskKey)).join();
  }

  private CheckDependencyRiskSupportedResponse checkSupported(SonarLintTestRpcServer backend, String configScopeId) {
    return backend.getDependencyRiskService().checkSupported(new CheckDependencyRiskSupportedParams(configScopeId)).join();
  }
}
