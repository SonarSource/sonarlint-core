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
package mediumtest.issues;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LocalOnlyIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ServerMatchedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TrackWithServerIssuesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType.BUG;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerIssueFixtures.aServerIssue;

class TrackWithServerIssuesMediumTests {

  public static final String CONFIG_SCOPE_ID = "configScopeId";

  @SonarLintTest
  void it_should_not_track_server_issues_when_configuration_scope_is_not_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .build();

    var response = trackWithServerIssues(backend,
      new TrackWithServerIssuesParams(CONFIG_SCOPE_ID, Map.of(Path.of("file/path"), List.of(new ClientTrackedFindingDto(null, null, null, null, "ruleKey", "message"))), false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .satisfies(result -> assertThat(result.getIssuesByIdeRelativePath())
        .hasEntrySatisfying(Path.of("file/path"), issues -> assertThat(issues).hasSize(1).allSatisfy(issue -> assertThat(issue.isRight()).isTrue())));
  }

  @SonarLintTest
  void it_should_track_local_only_issues(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .build();

    var response = trackWithServerIssues(backend, new TrackWithServerIssuesParams(CONFIG_SCOPE_ID,
      Map.of(Path.of("file/path"),
        List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(20))
      .satisfies(result -> assertThat(result.getIssuesByIdeRelativePath())
        .hasEntrySatisfying(Path.of("file/path"), issues -> {
          assertThat(issues).hasSize(1).allSatisfy(issue -> assertThat(issue.isRight()).isTrue());
          assertThat(issues).usingRecursiveComparison().ignoringFields("lsp4jEither.right.id")
            .isEqualTo(List.of(Either.forRight(new LocalOnlyIssueDto(null, null))));
        }));
  }

  @SonarLintTest
  void it_should_track_issues_for_unknown_branch(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .build();

    var response = trackWithServerIssues(backend, new TrackWithServerIssuesParams(CONFIG_SCOPE_ID,
      Map.of(Path.of("file/path"),
        List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .satisfies(result -> assertThat(result.getIssuesByIdeRelativePath())
        .hasEntrySatisfying(Path.of("file/path"), issues -> {
          assertThat(issues).hasSize(1).allSatisfy(issue -> assertThat(issue.isRight()).isTrue());
          assertThat(issues).usingRecursiveComparison().ignoringFields("lsp4jEither.right.id")
            .isEqualTo(List.of(Either.forRight(new LocalOnlyIssueDto(null, null))));
        }));
  }

  @SonarLintTest
  void it_should_track_with_a_known_server_issue_at_the_same_location(SonarLintTestHarness harness) {
    var configScopeId = CONFIG_SCOPE_ID;
    var connectionId = "connectionId";
    var projectKey = "projectKey";
    var serverIssue = aServerIssue("issueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
    var client = harness.newFakeClient().build();
    var server = harness.newFakeSonarQubeServer().withProject("projectKey").start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(connectionId, server, storage -> storage
        .withProject(projectKey, project -> project.withMainBranch("main", branch -> branch.withIssue(serverIssue))))
      .withBoundConfigScope(configScopeId, connectionId, projectKey)
      .build(client);
    backend.getConfigurationService().didAddConfigurationScopes(
      new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto(configScopeId, null, true, "name", new BindingConfigurationDto(connectionId, projectKey, true)))));

    var response = trackWithServerIssues(backend, new TrackWithServerIssuesParams(configScopeId,
      Map.of(Path.of("file/path"),
        List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(20))
      .satisfies(result -> assertThat(result.getIssuesByIdeRelativePath())
        .hasEntrySatisfying(Path.of("file/path"), issues -> assertThat(issues).usingRecursiveComparison().ignoringFields("lsp4jEither.left.id")
          .isEqualTo(
            List.of((Either.forLeft(
              new ServerMatchedIssueDto(null, "issueKey", 1000L, false, null, BUG, true)))))));
  }

  @SonarLintTest
  void it_should_translate_paths_before_matching(SonarLintTestHarness harness) {
    var serverFilePath = "server/file/path";
    var ideFilePath = "ide/file/path";
    var ruleKey = "rule:key";
    var issueKey = "issueKey";
    var server = harness.newFakeSonarQubeServer().withProject("projectKey", project -> project.withFile(serverFilePath)).start();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(new ClientFileDto(URI.create("file://foo"), Paths.get(ideFilePath), CONFIG_SCOPE_ID, null, null, null, null, null, true)))
      .build();
    var serverIssue = aServerIssue(issueKey)
      .withFilePath(serverFilePath)
      .withRuleKey(ruleKey)
      .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash"))
      .withIntroductionDate(Instant.ofEpochMilli(123456789L)).withType(RuleType.BUG);
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", b -> b.withIssue(serverIssue))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .build(client);

    var response = trackWithServerIssues(backend, new TrackWithServerIssuesParams(CONFIG_SCOPE_ID,
      Map.of(Path.of(ideFilePath),
        List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), ruleKey, "message"))),
      false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(20))
      .satisfies(result -> assertThat(result.getIssuesByIdeRelativePath())
        .hasEntrySatisfying(Path.of(ideFilePath), issues -> assertThat(issues).usingRecursiveComparison().ignoringFields("lsp4jEither.left.id")
          .isEqualTo(
            List.of(Either.forLeft(new ServerMatchedIssueDto(null, issueKey, 123456789L, false, null, BUG, true))))));
  }

  @SonarLintTest
  void it_should_download_all_issues_at_once_when_tracking_issues_from_more_than_10_files(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("9.9").withProject("projectKey",
      project -> project.withBranch("main",
        branch -> branch.withIssue("issueKey", "rule:key", "message", "author", "file/path", "OPEN", null, Instant.now(), new TextRange(1, 2, 3, 4))))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage.withServerVersion("9.5")
        .withProject("projectKey", project -> project.withMainBranch("main")))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .build(client);
    var issuesByIdeRelativePath = IntStream.rangeClosed(1, 11).boxed().collect(Collectors.<Integer, Path, List<ClientTrackedFindingDto>>toMap(index -> Path.of("file/path" + index),
      i -> List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "rule:key", "message"))));

    var response = trackWithServerIssues(backend, new TrackWithServerIssuesParams(CONFIG_SCOPE_ID, issuesByIdeRelativePath, true));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(4))
      .satisfies(result -> assertThat(result.getIssuesByIdeRelativePath())
        .hasSize(11));
    waitAtMost(2, SECONDS).untilAsserted(() -> server.getMockServer().verify(getRequestedFor(urlEqualTo("/api/issues/pull?projectKey=projectKey&branchName=main"))));
  }

  private CompletableFuture<TrackWithServerIssuesResponse> trackWithServerIssues(SonarLintTestRpcServer backend, TrackWithServerIssuesParams params) {
    return backend.getIssueTrackingService().trackWithServerIssues(params);
  }
}
