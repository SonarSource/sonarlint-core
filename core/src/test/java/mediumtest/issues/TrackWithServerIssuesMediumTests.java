/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import mediumtest.fixtures.ServerFixture;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.ClientTrackedIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.LocalOnlyIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.ServerMatchedIssueDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.tracking.TrackWithServerIssuesResponse;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.storage.ServerIssueFixtures.aServerIssue;
import static org.assertj.core.api.Assertions.assertThat;

class TrackWithServerIssuesMediumTests {

  private SonarLintBackendImpl backend;
  private ServerFixture.Server server;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
    if (server != null) {
      server.shutdown();
      server = null;
    }
  }

  @Test
  void it_should_not_track_server_issues_when_configuration_scope_is_not_bound() {
    backend = newBackend()
      .withUnboundConfigScope("configScopeId")
      .build();

    var response = trackWithServerIssues(
      new TrackWithServerIssuesParams("configScopeId", Map.of("filePath", List.of(new ClientTrackedIssueDto(null, null, null, null, "ruleKey", "message"))), false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .satisfies(result -> assertThat(result.getIssuesByServerRelativePath())
        .hasEntrySatisfying("filePath", issues -> assertThat(issues).hasSize(1).allSatisfy(issue -> assertThat(issue.isRight()).isTrue())));
  }

  @Test
  void it_should_track_local_only_issues() {
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .build();

    var response = trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of("file/path", List.of(new ClientTrackedIssueDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .satisfies(result -> assertThat(result.getIssuesByServerRelativePath())
        .hasEntrySatisfying("file/path", issues -> {
          assertThat(issues).hasSize(1).allSatisfy(issue -> assertThat(issue.isRight()).isTrue());
          assertThat(issues).usingRecursiveComparison().ignoringFields("right.id")
            .isEqualTo(List.of(Either.<ServerMatchedIssueDto, LocalOnlyIssueDto>forRight(new LocalOnlyIssueDto(null, null))));
        }));
  }

  @Test
  void it_should_track_issues_for_unknown_branch() {
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of("file/path", List.of(new ClientTrackedIssueDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .satisfies(result -> assertThat(result.getIssuesByServerRelativePath())
        .hasEntrySatisfying("file/path", issues -> {
          assertThat(issues).hasSize(1).allSatisfy(issue -> assertThat(issue.isRight()).isTrue());
          assertThat(issues).usingRecursiveComparison().ignoringFields("right.id")
            .isEqualTo(List.of(Either.<ServerMatchedIssueDto, LocalOnlyIssueDto>forRight(new LocalOnlyIssueDto(null, null))));
        }));
  }

  @Test
  void it_should_track_with_a_known_server_issue_at_the_same_location() {
    var serverIssue = aServerIssue("issueKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1)).withType(RuleType.BUG);
    backend = newBackend()
      .withSonarQubeConnection("connectionId", storage -> storage
        .withProject("projectKey", project -> project.withBranch("main", branch -> branch.withIssue(serverIssue))))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .build();

    var response = trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of("file/path", List.of(new ClientTrackedIssueDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .satisfies(result -> assertThat(result.getIssuesByServerRelativePath())
        .hasEntrySatisfying("file/path", issues -> assertThat(issues).usingRecursiveComparison().ignoringFields("left.id")
          .isEqualTo(List.of(Either.<ServerMatchedIssueDto, LocalOnlyIssueDto>forLeft(new ServerMatchedIssueDto(null, "issueKey", 1000L, false, null, RuleType.BUG))))));
  }

  @Test
  void it_should_track_with_a_server_only_issue_when_fetching_from_legacy_server_requested() {
    server = ServerFixture.newSonarQubeServer("9.5").withProject("projectKey",
      project -> project.withBranch("main", branch -> branch.withIssue("issueKey", "rule:key", "message", "author", "file/path", "OPEN", null, new TextRange(1, 2, 3, 4)))).start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .build();

    var response = trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId",
      Map.of("file/path", List.of(new ClientTrackedIssueDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "rule:key", "message"))),
      true));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .satisfies(result -> assertThat(result.getIssuesByServerRelativePath())
        .hasEntrySatisfying("file/path", issues -> assertThat(issues).usingRecursiveComparison().ignoringFields("left.id")
          .isEqualTo(List.of(Either.<ServerMatchedIssueDto, LocalOnlyIssueDto>forLeft(new ServerMatchedIssueDto(null, "issueKey", 123456789L, false, null, RuleType.BUG))))));
  }

  @Test
  void it_should_download_all_issues_at_once_when_tracking_issues_from_more_than_10_files() {
    server = ServerFixture.newSonarQubeServer("9.5").withProject("projectKey",
      project -> project.withBranch("main", branch -> branch.withIssue("issueKey", "rule:key", "message", "author", "file/path", "OPEN", null, new TextRange(1, 2, 3, 4)))).start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage.withServerVersion("9.5"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey", "main")
      .build();
    var issuesByServerRelativePath = IntStream.rangeClosed(1, 11).boxed().collect(Collectors.<Integer, String, List<ClientTrackedIssueDto>>toMap(index -> "file/path" + index,
      i -> List.of(new ClientTrackedIssueDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "rule:key", "message"))));

    var response = trackWithServerIssues(new TrackWithServerIssuesParams("configScopeId", issuesByServerRelativePath, true));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(4))
      .satisfies(result -> assertThat(result.getIssuesByServerRelativePath())
        .hasSize(11));
    var lastRequest = server.lastRequest();
    assertThat(lastRequest.getPath()).isEqualTo("/batch/issues?key=projectKey&branch=main");
  }

  private CompletableFuture<TrackWithServerIssuesResponse> trackWithServerIssues(TrackWithServerIssuesParams params) {
    return backend.getIssueTrackingService().trackWithServerIssues(params);
  }
}
