/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2024 SonarSource SA
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
package mediumtest.tracking;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.TrackedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static mediumtest.fixtures.storage.ServerIssueFixtures.aServerIssue;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.INSTANT;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class IssueTrackingMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private SonarLintTestRpcServer backend;

  @AfterEach
  void stop() {
    if (backend != null) {
      backend.shutdown();
    }
  }

  @Test
  void it_should_start_tracking_an_issue_in_standalone_mode_when_detected_for_the_first_time(@TempDir Path baseDir) {
    var filePath = createFileWithAnXmlIssue(baseDir);
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);
    var startTime = System.currentTimeMillis();

    var publishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(publishedIssue)
      .extracting(TrackedIssueDto::getIntroductionDate, as(INSTANT))
      .isAfter(Instant.ofEpochMilli(startTime));
  }

  @Test
  void it_should_match_an_already_tracked_issue_in_standalone_mode_when_detected_for_the_second_time(@TempDir Path baseDir) {
    var filePath = createFileWithAnXmlIssue(baseDir);
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);

    var firstPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    var newPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(newPublishedIssue)
      .extracting(TrackedIssueDto::getId, TrackedIssueDto::getIntroductionDate)
      .containsExactly(firstPublishedIssue.getId(), firstPublishedIssue.getIntroductionDate());
  }

  @Test
  void it_should_start_tracking_an_issue_in_connected_mode_when_detected_for_the_first_time(@TempDir Path baseDir) {
    var filePath = createFileWithAnXmlIssue(baseDir);
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withPlugin(TestPlugin.XML).withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MINOR"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .build(client);
    var startTime = System.currentTimeMillis();

    var publishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(publishedIssue)
      .extracting(TrackedIssueDto::getIntroductionDate, as(INSTANT))
      .isAfter(Instant.ofEpochMilli(startTime));
  }

  @Test
  void it_should_match_an_already_tracked_issue_in_connected_mode_when_detected_for_the_second_time(@TempDir Path baseDir) {
    var filePath = createFileWithAnXmlIssue(baseDir);
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withPlugin(TestPlugin.XML).withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MINOR"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .build(client);

    var firstPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    var newPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(newPublishedIssue)
      .extracting(TrackedIssueDto::getId, TrackedIssueDto::getIntroductionDate)
      .containsExactly(firstPublishedIssue.getId(), firstPublishedIssue.getIntroductionDate());
  }

  @Test
  void it_should_match_a_local_issue_with_a_server_issue_in_connected_mode_when_detected_for_the_first_time(@TempDir Path baseDir) {
    var filePath = createFileWithAnXmlIssue(baseDir);
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    var serverIssueIntroductionDate = Instant.ofEpochMilli(12345678);
    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withPlugin(TestPlugin.XML).withProject("projectKey",
          project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MINOR")).withMainBranch("main", branch -> branch.withIssue(aServerIssue("key").withIntroductionDate(serverIssueIntroductionDate)))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .build(client);

    var publishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(publishedIssue)
      .extracting(TrackedIssueDto::getServerKey, TrackedIssueDto::getIntroductionDate)
      .containsExactly("key", serverIssueIntroductionDate);
  }

  @Test
  void it_should_match_a_previously_tracked_issue_with_a_server_issue_when_binding(@TempDir Path baseDir) {
    var filePath = createFileWithAnXmlIssue(baseDir);
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    var serverIssueIntroductionDate = Instant.ofEpochMilli(12345678);
    backend = newBackend()
      .withSonarQubeConnection("connectionId",
        storage -> storage.withPlugin(TestPlugin.XML).withProject("projectKey",
          project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MINOR")).withMainBranch("main", branch -> branch.withIssue(aServerIssue("key").withIntroductionDate(serverIssueIntroductionDate)))))
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .build(client);

    var firstPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(CONFIG_SCOPE_ID, new BindingConfigurationDto("connectionId", "projectKey", true)));

    var newPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(newPublishedIssue)
      .extracting(TrackedIssueDto::getId, TrackedIssueDto::getServerKey, TrackedIssueDto::getIntroductionDate)
      .containsExactly(firstPublishedIssue.getId(), "key", serverIssueIntroductionDate);
  }

  private TrackedIssueDto analyzeFileAndGetIssue(URI fileUri, SonarLintRpcClientDelegate client) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), System.currentTimeMillis()))
      .join();
    var publishedIssuesByFile = getPublishedIssues(client, analysisId);
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    assertThat(publishedIssuesByFile).containsOnlyKeys(fileUri);
    var publishedIssues = publishedIssuesByFile.get(fileUri);
    assertThat(publishedIssues).hasSize(1);
    return publishedIssues.get(0);
  }

  private static Path createFileWithAnXmlIssue(Path folderPath) {
    return createFile(folderPath, "pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<project>\n"
        + "  <modelVersion>4.0.0</modelVersion>\n"
        + "  <groupId>com.foo</groupId>\n"
        + "  <artifactId>bar</artifactId>\n"
        + "  <version>${pom.version}</version>\n"
        + "</project>");
  }

  private Map<URI, List<TrackedIssueDto>> getPublishedIssues(SonarLintRpcClientDelegate client, UUID analysisId) {
    ArgumentCaptor<Map<URI, List<TrackedIssueDto>>> trackedIssuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(client).publishIssues(eq(CONFIG_SCOPE_ID), trackedIssuesCaptor.capture(), eq(false), eq(analysisId));
    return trackedIssuesCaptor.getValue();
  }

  private static Path createFile(Path folderPath, String fileName, String content) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.writeString(filePath, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return filePath;
  }
}
