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
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.TrackedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Qualityprofiles;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;

import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static mediumtest.fixtures.storage.ServerIssueFixtures.aServerIssue;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.INSTANT;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static testutils.TestUtils.protobufBody;

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
  void it_should_track_file_level_issue(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "Fubar.java",
      "public interface Fubar\n" +
        "{}");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    var firstPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    var newPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(newPublishedIssue)
      .extracting(TrackedIssueDto::getId, TrackedIssueDto::getIntroductionDate)
      .containsExactly(firstPublishedIssue.getId(), firstPublishedIssue.getIntroductionDate());
  }


  @Test
  void it_should_test_quick_fixes(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "FileHelper.java",
      "package myapp.helpers;\n" +
        "\n" +
        "import java.io.IOException;\n" +
        "import java.nio.file.*;\n" +
        "import java.lang.Runnable;  // Noncompliant - java.lang is imported by default\n" +
        "\n" +
        "public class FileHelper {\n" +
        "    public static String readFirstLine(String filePath) throws IOException {\n" +
        "        return Files.readAllLines(Paths.get(filePath)).get(0);\n" +
        "    }\n" +
        "}");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    var issues = analyzeFileAndGetAllIssues(fileUri, client);

    assertThat(issues)
      .extracting("ruleKey", "primaryMessage", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset")
      .contains(tuple("java:S1128", "Remove this unnecessary import: java.lang classes are always implicitly imported.", 5, 7, 5, 25));
    var issue = issues.stream().filter(i -> i.getRuleKey().equals("java:S1128")).findFirst().get();
    var quickFixes = issue.getQuickFixes();
    assertThat(quickFixes).isNotEmpty();
    var quickFix = quickFixes.get(0);
    assertThat(quickFix.message()).isEqualTo("Remove the import");
    var fileEdits = quickFix.fileEdits();
    assertThat(quickFixes).isNotEmpty();
    var fileEdit = fileEdits.get(0);
    assertThat(fileEdit.target()).isEqualTo(fileUri);
    assertThat(fileEdit.textEdits().get(0).newText()).isEmpty();
    var textRange = fileEdit.textEdits().get(0).range();
    assertThat(textRange.getStartLine()).isEqualTo(4);
    assertThat(textRange.getStartLineOffset()).isEqualTo(23);
    assertThat(textRange.getEndLine()).isEqualTo(5);
    assertThat(textRange.getEndLineOffset()).isEqualTo(26);
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
    var server = newSonarQubeServer()
      .withProject("projectKey").start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withPlugin(TestPlugin.XML).withProject("projectKey",
          project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MINOR"))
            .withMainBranch("main", branch -> branch.withIssue(
              aServerIssue("key")
                .withFilePath(filePath.toString())
                .withRuleKey("xml:S3421")
                .withTextRange(new TextRangeWithHash(1, 2, 3, 4, ""))
                .withIntroductionDate(serverIssueIntroductionDate)))))
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
    var server = newSonarQubeServer()
      .withProject("projectKey")
      .withQualityProfile("qp", qualityProfile -> qualityProfile.withLanguage("xml")
        .withActiveRule("xml:S3421", activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR)))
      .start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server,
        storage -> storage.withPlugin(TestPlugin.XML).withProject("projectKey",
          project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "MINOR"))
            .withMainBranch("main", branch -> branch.withIssue(
              aServerIssue("key")
                .withFilePath(filePath.toString())
                .withRuleKey("xml:S3421")
                .withTextRange(new TextRangeWithHash(1, 2, 3, 4, ""))
                .withIntroductionDate(serverIssueIntroductionDate)))))
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);

    var firstPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    backend.getConfigurationService().didUpdateBinding(new DidUpdateBindingParams(CONFIG_SCOPE_ID,
      new BindingConfigurationDto("connectionId", "projectKey", true)));
    server.getMockServer().stubFor(get("/api/qualityprofiles/search.protobuf?project=projectKey")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Qualityprofiles.SearchWsResponse.newBuilder().addProfiles(
        Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder()
          .setKey("qualityProfileKey")
          .setLanguage("xml")
          .setLanguageName("xml")
          .setName("Quality Profile")
          .setRulesUpdatedAt(Instant.now().toString())
          .setUserUpdatedAt(Instant.now().toString())
          .setIsDefault(true)
          .setActiveRuleCount(1)
          .build()
      ).build()))));

    var newPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(newPublishedIssue)
      .extracting(TrackedIssueDto::getId, TrackedIssueDto::getServerKey, TrackedIssueDto::getIntroductionDate)
      .containsExactly(firstPublishedIssue.getId(), "key", serverIssueIntroductionDate);
  }

  private List<TrackedIssueDto> analyzeFileAndGetAllIssues(URI fileUri, SonarLintRpcClientDelegate client) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), true, System.currentTimeMillis()))
      .join();
    var publishedIssuesByFile = getPublishedIssues(client, analysisId);
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    assertThat(publishedIssuesByFile).containsOnlyKeys(fileUri);
    return publishedIssuesByFile.get(fileUri);
  }

  private TrackedIssueDto analyzeFileAndGetIssue(URI fileUri, SonarLintRpcClientDelegate client) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), true, System.currentTimeMillis()))
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
