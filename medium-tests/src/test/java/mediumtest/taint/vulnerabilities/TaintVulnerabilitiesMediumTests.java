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
package mediumtest.taint.vulnerabilities;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.DidUpdateConnectionsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.GetEffectiveIssueDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ListAllParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerTaintIssueFixtures.aServerTaintIssue;

class TaintVulnerabilitiesMediumTests {

  @SonarLintTest
  void it_should_return_no_taint_vulnerabilities_if_the_scope_is_not_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .build();

    var taintVulnerabilities = listAllTaintVulnerabilities(backend, "configScopeId");

    assertThat(taintVulnerabilities).isEmpty();
  }

  @SonarLintTest
  void it_should_return_no_taint_vulnerabilities_if_the_storage_is_empty(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var taintVulnerabilities = listAllTaintVulnerabilities(backend, "configScopeId");

    assertThat(taintVulnerabilities).isEmpty();
  }

  @SonarLintTest
  void it_should_return_the_stored_taint_vulnerabilities(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey", project -> project.withBranch("main"))
      .start();
    var introductionDate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main",
            branch -> branch.withTaintIssue(aServerTaintIssue("key")
              .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withRuleKey("ruleKey")
              .withType(RuleType.VULNERABILITY)
              .withIntroductionDate(introductionDate)))))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withFullSynchronization()
      .build();

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(listAllTaintVulnerabilities(backend, "configScopeId"))
      .extracting(TaintVulnerabilityDto::getIntroductionDate)
      .containsOnly(introductionDate));
  }

  @SonarLintTest
  void it_should_return_taint_details(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();

    var server = harness.newFakeSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java").withActiveRule("javasecurity:S6549", activeRule -> activeRule
        .withSeverity(org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity.MAJOR)))
      .withProject("projectKey", project -> project.withBranch("main")
        .withQualityProfile("qpKey"))
      .withPlugin(TestPlugin.JAVA)
      .start();
    var introductionDate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    var fakeTaintBuilder = aServerTaintIssue("key")
      .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withRuleKey("javasecurity:S6549")
      .withType(RuleType.VULNERABILITY)
      .withIntroductionDate(introductionDate);

    var backend = harness.newBackend()
      .withExtraEnabledLanguagesInConnectedMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main",
            branch -> branch.withTaintIssue(fakeTaintBuilder))))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withFullSynchronization()
      .build(client);

    client.waitForSynchronization();

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(listAllTaintVulnerabilities(backend, "configScopeId"))
      .extracting(TaintVulnerabilityDto::getIntroductionDate)
      .containsOnly(introductionDate));
    var actualTaintId = listAllTaintVulnerabilities(backend, "configScopeId").get(0).getId();

    var taintDetails = backend.getIssueService().getEffectiveIssueDetails(new GetEffectiveIssueDetailsParams("configScopeId", actualTaintId)).join();

    assertThat(taintDetails).isNotNull();
  }

  @SonarLintTest
  void it_should_refresh_taint_vulnerabilities_when_requested(SonarLintTestHarness harness) {
    var serverWithATaint = harness.newFakeSonarQubeServer()
      .withProject("projectKey", project -> project.withBranch("main", branch -> branch.withTaintIssue("oldIssueKey", "rule:key", "message", "author", "file/path", "OPEN", null,
        Instant.now(), new TextRange(1, 2, 3, 4), RuleType.VULNERABILITY)))
      .start();
    var newestIntroductionDate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    var serverWithAnotherTaint = harness.newFakeSonarQubeServer()
      .withProject("projectKey",
        project -> project.withBranch("main", branch -> branch.withTaintIssue("anotherIssueKey", "rule:key", "message", "author", "file/path", "OPEN", null,
          newestIntroductionDate, new TextRange(1, 2, 3, 4), RuleType.VULNERABILITY)))
      .start();
    var backend = harness.newBackend()
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withSonarQubeConnection("connectionId", serverWithATaint,
        storage -> storage.withProject("projectKey",
          project -> project.withMainBranch("main")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .withFullSynchronization()
      .build();
    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(listAllTaintVulnerabilities(backend, "configScopeId")).isNotEmpty());
    // switch server to simulate a new dataset. Not ideal, should be handled differently
    backend.getConnectionService()
      .didUpdateConnections(new DidUpdateConnectionsParams(List.of(new SonarQubeConnectionConfigurationDto("connectionId", serverWithAnotherTaint.baseUrl(), true)), List.of()));

    var taintVulnerabilities = refreshAndListAllTaintVulnerabilities(backend, "configScopeId");

    assertThat(taintVulnerabilities)
      .extracting(TaintVulnerabilityDto::getIntroductionDate)
      .contains(newestIntroductionDate);
  }

  private List<TaintVulnerabilityDto> listAllTaintVulnerabilities(SonarLintTestRpcServer backend, String configScopeId) {
    try {
      return backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams(configScopeId)).get().getTaintVulnerabilities();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private List<TaintVulnerabilityDto> refreshAndListAllTaintVulnerabilities(SonarLintTestRpcServer backend, String configScopeId) {
    try {
      return backend.getTaintVulnerabilityTrackingService().listAll(new ListAllParams(configScopeId, true)).get().getTaintVulnerabilities();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
