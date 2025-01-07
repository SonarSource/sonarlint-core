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
package mediumtest.hotspots;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ClientTrackedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LineWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LocalOnlySecurityHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.MatchWithServerSecurityHotspotsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.MatchWithServerSecurityHotspotsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ServerMatchedSecurityHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TextRangeWithHashDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.test.utils.storage.ServerSecurityHotspotFixture.aServerHotspot;

class MatchWithServerHotspotsMediumTests {

  @SonarLintTest
  void it_should_not_track_server_hotspots_when_configuration_scope_is_not_bound(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .withUnboundConfigScope("configScopeId")
      .build();

    var response = matchWithServerHotspots(
      backend, new MatchWithServerSecurityHotspotsParams("configScopeId", Map.of(Path.of("filePath"), List.of(new ClientTrackedFindingDto(null, null, null, null, "ruleKey", "message"))), false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .satisfies(result -> assertThat(result.getSecurityHotspotsByIdeRelativePath())
        .hasEntrySatisfying(Path.of("filePath"), hotspots -> assertThat(hotspots).hasSize(1).allSatisfy(hotspot -> assertThat(hotspot.isRight()).isTrue())));
  }

  @SonarLintTest
  void it_should_track_local_only_hotspots(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = matchWithServerHotspots(backend, new MatchWithServerSecurityHotspotsParams("configScopeId",
      Map.of(Path.of("file/path"), List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .satisfies(result -> assertThat(result.getSecurityHotspotsByIdeRelativePath())
        .hasEntrySatisfying(Path.of("file/path"), hotspots -> {
          assertThat(hotspots).hasSize(1).allSatisfy(hotspot -> assertThat(hotspot.isRight()).isTrue());
          assertThat(hotspots).usingRecursiveComparison().ignoringFields("lsp4jEither.right.id")
            .isEqualTo(List.of(Either.forRight(new LocalOnlySecurityHotspotDto(null))));
        }));
  }

  @SonarLintTest
  void it_should_track_hotspots_for_unknown_branch(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer().start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = matchWithServerHotspots(backend, new MatchWithServerSecurityHotspotsParams("configScopeId",
      Map.of(Path.of("file/path"), List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(20))
      .satisfies(result -> assertThat(result.getSecurityHotspotsByIdeRelativePath())
        .hasEntrySatisfying(Path.of("file/path"), hotspots -> {
          assertThat(hotspots).hasSize(1).allSatisfy(hotspot -> assertThat(hotspot.isRight()).isTrue());
          assertThat(hotspots).usingRecursiveComparison().ignoringFields("lsp4jEither.right.id")
            .isEqualTo(List.of(Either.forRight(new LocalOnlySecurityHotspotDto(null))));
        }));
  }

  @SonarLintTest
  void it_should_track_with_a_known_server_hotspot_at_the_same_location(SonarLintTestHarness harness) {
    var serverHotspot = aServerHotspot("hotspotKey").withTextRange(new TextRangeWithHash(1, 2, 3, 4, "hash")).withIntroductionDate(Instant.EPOCH.plusSeconds(1))
      .withStatus(HotspotReviewStatus.SAFE);
    var client = harness.newFakeClient().build();
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey").start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server, storage -> storage
        .withProject("projectKey", project -> project.withMainBranch("main", branch -> branch.withHotspot(serverHotspot))))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    var response = matchWithServerHotspots(backend, new MatchWithServerSecurityHotspotsParams("configScopeId",
      Map.of(Path.of("file/path"), List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "ruleKey", "message"))),
      false));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .satisfies(result -> assertThat(result.getSecurityHotspotsByIdeRelativePath())
        .hasEntrySatisfying(Path.of("file/path"), hotspots -> assertThat(hotspots).usingRecursiveComparison().ignoringFields("lsp4jEither.left.id")
          .isEqualTo(List.of(Either.forLeft(
            new ServerMatchedSecurityHotspotDto(null, "hotspotKey", 1000L, HotspotStatus.SAFE, true))))));
  }

  @SonarLintTest
  void it_should_track_with_a_server_only_hotspot_when_fetching_from_legacy_server_requested(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.0").withProject("projectKey",
        project -> project.withBranch("main", branch -> branch.withHotspot("hotspotKey",
          hotspot -> hotspot.withRuleKey("rule:key").withMessage("message").withFilePath("file/path").withAuthor("author").withStatus(HotspotReviewStatus.TO_REVIEW)
            .withCreationDate(Instant.ofEpochMilli(123456789))
            .withTextRange(new TextRange(1, 2, 3, 4)))))
      .start();
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server, storage -> storage.withServerVersion("10.0")
        .withProject("projectKey", project -> project.withMainBranch("main")))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build(client);

    var response = matchWithServerHotspots(backend, new MatchWithServerSecurityHotspotsParams("configScopeId",
      Map.of(Path.of("file/path"),
        List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "rule:key", "message"))),
      true));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .satisfies(result -> assertThat(result.getSecurityHotspotsByIdeRelativePath())
        .hasEntrySatisfying(Path.of("file/path"), hotspots -> assertThat(hotspots).usingRecursiveComparison().ignoringFields("lsp4jEither.left.id")
          .isEqualTo(List.of(Either.forLeft(
            new ServerMatchedSecurityHotspotDto(null, "hotspotKey", 123456000L, HotspotStatus.TO_REVIEW, true))))));
  }

  @SonarLintTest
  void it_should_download_all_hotspots_at_once_when_tracking_hotspots_from_more_than_10_files(SonarLintTestHarness harness) {
    var server = harness.newFakeSonarQubeServer("10.0").withProject("projectKey",
        project -> project.withBranch("main",
          branch -> branch.withIssue("issueKey", "rule:key", "message", "author", "file/path", "OPEN", null, Instant.now(), new TextRange(1, 2, 3, 4))))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(), storage -> storage.withServerVersion("9.5"))
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();
    var hotspotsByServerRelativePath = IntStream.rangeClosed(1, 11).boxed().collect(Collectors.<Integer, Path, List<ClientTrackedFindingDto>>toMap(index -> Path.of("file/path" + index),
      i -> List.of(new ClientTrackedFindingDto(null, null, new TextRangeWithHashDto(1, 2, 3, 4, "hash"), new LineWithHashDto(1, "linehash"), "rule:key", "message"))));

    var response = matchWithServerHotspots(backend, new MatchWithServerSecurityHotspotsParams("configScopeId", hotspotsByServerRelativePath, true));

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(4))
      .satisfies(result -> assertThat(result.getSecurityHotspotsByIdeRelativePath())
        .hasSize(11));
  }

  private CompletableFuture<MatchWithServerSecurityHotspotsResponse> matchWithServerHotspots(SonarLintTestRpcServer backend, MatchWithServerSecurityHotspotsParams params) {
    return backend.getSecurityHotspotMatchingService().matchWithServerSecurityHotspots(params);
  }
}
