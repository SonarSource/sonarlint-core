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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonar.scanner.protocol.Constants;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Qualityprofiles;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.commit;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.commitAtDate;
import static org.sonarsource.sonarlint.core.commons.testutils.GitUtils.createRepository;
import static testutils.TestUtils.protobufBody;
import static testutils.plugins.SonarPluginBuilder.newSonarPlugin;

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
  void it_should_raise_tracked_and_untracked_issues_in_standalone_mode(@TempDir Path baseDir) {
    var ideFilePath = "Foo.java";
    var filePath = createFile(baseDir, ideFilePath,
      "package sonar;\n" +
        "// FIXME foo bar\n" +
        "public interface Foo {\n" +
        "}");
    var projectKey = "projectKey";
    var connectionId = "connectionId";
    var branchName = "main";
    var ruleKey = "java:S1134";

    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend()
      .withSonarQubeConnection(connectionId, storage -> storage.withPlugin(TestPlugin.JAVA).withProject(projectKey,
        project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule(ruleKey, "MINOR"))
          .withMainBranch(branchName)))
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .build(client);

    var firstAnalysisPublishedIssues = analyzeFileAndGetAllIssuesOfRule(fileUri, client, ruleKey);

    assertThat(firstAnalysisPublishedIssues).hasSize(1);
    changeFileContent(baseDir, ideFilePath,
      "package sonar;\n" +
        "// FIXME foo bar\n" +
        "public interface Foo {\n" +
        "// FIXME bar baz\n" +
        "}");
    var secondAnalysisPublishedIssues = analyzeFileAndGetAllIssuesOfRule(fileUri, client, ruleKey);
    assertThat(secondAnalysisPublishedIssues).hasSize(2);
  }

  @Disabled("https://sonarsource.atlassian.net/browse/SLCORE-873")
  @Test
  void it_should_raise_tracked_and_untracked_issues_after_match_with_server_issues(@TempDir Path baseDir) {
    var ideFilePath = "Foo.java";
    var filePath = createFile(baseDir, ideFilePath,
      "// FIXME foo bar\n" +
        "public class Foo {\n" +
        "}");
    var projectKey = "projectKey";
    var connectionId = "connectionId";
    var branchName = "main";
    var ruleKey = "java:S1134";
    var message = "Take the required action to fix the issue indicated by this comment.";

    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    var server = newSonarQubeServer("9.9")
      .withProject("projectKey", project -> project.withBranch("main", branch -> branch
        .withIssue("uuid", "java:S1134", message, "author", ideFilePath, "395d7a96efa8afd1b66ab6b680d0e637", Constants.Severity.BLOCKER,
          org.sonarsource.sonarlint.core.commons.RuleType.BUG,
          "OPEN", null, Instant.ofEpochMilli(123456789L), new TextRange(2, 0, 2, 16))))
      .withQualityProfile("qp", qualityProfile -> qualityProfile.withLanguage("java")
        .withActiveRule(ruleKey, activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR)))
      .start();
    backend = newBackend()
      .withSonarQubeConnection(connectionId, server,
        storage -> storage.withPlugin(TestPlugin.JAVA).withProject(projectKey,
          project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule(ruleKey, "MINOR"))
            .withMainBranch(branchName)))
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    backend.getConfigurationService()
      .didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
        new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID,
          new BindingConfigurationDto(connectionId, projectKey, true)))));

    var firstAnalysisPublishedIssues = analyzeFileAndGetAllIssues(fileUri, client);

    assertThat(firstAnalysisPublishedIssues).hasSize(1);
    changeFileContent(baseDir, ideFilePath,
      "package sonar;\n" +
        "// FIXME foo bar\n" +
        "public interface Foo {\n" +
        "// FIXME bar baz\n" +
        "}");
    var secondAnalysisPublishedIssues = analyzeFileAndGetAllIssues(fileUri, client);
    assertThat(secondAnalysisPublishedIssues).hasSize(2);
  }

  @Disabled("https://sonarsource.atlassian.net/browse/SLCORE-873")
  @Test
  void it_should_use_server_new_code_definition_for_server_issues_and_set_true_for_unmatched_issues(@TempDir Path baseDir) {
    var ideFilePath = "Foo.java";
    var filePath = createFile(baseDir, ideFilePath,
      "// FIXME foo bar\n" +
        "// FIXME foo bar2\n" +
        "public class Foo {\n" +
        "}");
    var projectKey = "projectKey";
    var connectionId = "connectionId";
    var branchName = "main";
    var ruleKey = "java:S1134";
    var message = "Take the required action to fix the issue indicated by this comment.";

    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    var server = newSonarQubeServer("9.9")
      .withProject("projectKey", project -> project.withBranch("main", branch -> branch
        .withIssue("uuid1", "java:S1134", message, "author", ideFilePath, "395d7a96efa8afd1b66ab6b680d0e637", Constants.Severity.BLOCKER,
          org.sonarsource.sonarlint.core.commons.RuleType.BUG,
          "OPEN", null, Instant.now().minus(1, ChronoUnit.DAYS), new TextRange(1, 0, 1, 16))
        .withIssue("uuid2", "java:S1134", message, "author", ideFilePath, "395d7a96efa8afd1b66ab6b680d0e637", Constants.Severity.BLOCKER,
          org.sonarsource.sonarlint.core.commons.RuleType.BUG,
          "OPEN", null, Instant.now().plus(1, ChronoUnit.DAYS), new TextRange(2, 0, 2, 16))
      ))
      .withQualityProfile("qp", qualityProfile -> qualityProfile.withLanguage("java")
        .withActiveRule(ruleKey, activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR)))
      .start();
    backend = newBackend()
      .withSonarQubeConnection(connectionId, server,
        storage -> storage.withPlugin(TestPlugin.JAVA).withProject(projectKey,
          project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule(ruleKey, "MINOR"))
            .withNewCodeDefinition(
              Sonarlint.NewCodeDefinition.newBuilder().setMode(Sonarlint.NewCodeDefinitionMode.PREVIOUS_VERSION).setThresholdDate(Instant.now().toEpochMilli()).build())
            .withMainBranch(branchName)))
      .withFullSynchronization()
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    backend.getConfigurationService()
      .didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
        new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID,
          new BindingConfigurationDto(connectionId, projectKey, true)))));
    client.waitForSynchronization();

    var issues = analyzeFileAndGetAllIssues(fileUri, client);
    assertThat(issues).hasSize(2);
    assertThat(issues.stream().filter(raisedIssueDto -> raisedIssueDto.getServerKey().equals("uuid1")).findFirst().get().isOnNewCode()).isFalse();
    assertThat(issues.stream().filter(raisedIssueDto -> raisedIssueDto.getServerKey().equals("uuid2")).findFirst().get().isOnNewCode()).isTrue();

    changeFileContent(baseDir, ideFilePath,
      "package sonar;\n" +
        "// FIXME foo bar\n" +
        "// FIXME foo bar2\n" +
        "public interface Foo {\n" +
        "// FIXME bar baz\n" +
        "}");
    var secondAnalysisPublishedIssues = analyzeFileAndGetAllIssues(fileUri, client);
    assertThat(secondAnalysisPublishedIssues).hasSize(3);
    assertThat(secondAnalysisPublishedIssues.stream().filter(raisedIssueDto -> Objects.isNull(raisedIssueDto.getServerKey())).findFirst().get().isOnNewCode()).isTrue();
  }

  @Test
  void it_should_use_git_blame_to_set_introduction_date_for_git_repos(@TempDir Path baseDir) throws IOException, GitAPIException {
    var repository = createRepository(baseDir);
    var filePath = createFile(baseDir, "Foobar.java",
      "package sonar;\n" +
        "public interface Foobar\n" +
        "{}");
    var commitDate = commit(repository, filePath.getFileName().toString());
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    var issue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(issue.getIntroductionDate()).isEqualTo(commitDate.toInstant());
  }

  @Test
  void it_should_use_git_blame_to_set_introduction_date_for_git_repos_for_given_content(@TempDir Path baseDir) throws IOException, GitAPIException {
    var repository = createRepository(baseDir);
    String committedFileContent = "package sonar;\n" +
      "public interface Foobar\n" +
      "{}";
    var filePath = createFile(baseDir, "Foobar.java",
      committedFileContent);
    commit(repository, filePath.getFileName().toString());
    var fileUri = filePath.toUri();
    String unsavedFileContent = "package sonar;\n" +
      "public interface Foobar\n" +
      "//TODO introduce new issue\n" +
      "{}";
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, unsavedFileContent, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    Instant analysisTime = Instant.now();
    var issues = analyzeFileAndGetAllIssues(fileUri, client);

    assertThat(issues)
      .extracting(raisedIssueDto -> raisedIssueDto.getIntroductionDate().isAfter(analysisTime), raisedIssueDto -> raisedIssueDto.getTextRange().getStartLine())
      .containsExactlyInAnyOrder(tuple(false, 1), tuple(true, 3));
  }

  @Disabled("https://sonarsource.atlassian.net/browse/SLCORE-873")
  @Test
  void it_should_track_issue_secondary_locations(@TempDir Path baseDir) {
    var ideFilePath = "Foo.java";
    var filePath = createFile(baseDir, ideFilePath,
      "package devoxx;\n" +
        "\n" +
        "public class Foo {\n" +
        "  public void run() {\n" +
        "    prepare(\"action1\");\n" +
        "    execute(\"action1\");\n" +
        "    release(\"action1\");\n" +
        "  }\n" +
        "}\n");
    var projectKey = "projectKey";
    var connectionId = "connectionId";
    var branchName = "main";
    var ruleKey = "java:S1192";
    var message = "Define a constant instead of duplicating this literal \"action1\" 3 times.";

    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    var server = newSonarQubeServer("9.9")
      .withProject("projectKey", project -> project.withBranch("main", branch -> branch
        .withIssue("uuid", "java:S1192", message, "author", ideFilePath, "395d7a96efa8afd1b66ab6b680d0e637", Constants.Severity.BLOCKER,
          org.sonarsource.sonarlint.core.commons.RuleType.BUG,
          "OPEN", null, Instant.ofEpochMilli(123456789L), new TextRange(5, 12, 5, 21))))
      .withQualityProfile("qp", qualityProfile -> qualityProfile.withLanguage("java")
        .withActiveRule(ruleKey, activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR)))
      .start();
    backend = newBackend()
      .withSonarQubeConnection(connectionId, server,
        storage -> storage.withPlugin(TestPlugin.JAVA).withProject(projectKey,
          project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule(ruleKey, "MINOR"))
            .withMainBranch(branchName)))
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    backend.getConfigurationService()
      .didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
        new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID,
          new BindingConfigurationDto(connectionId, projectKey, true)))));

    var firstPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(firstPublishedIssue)
      .extracting("ruleKey", "primaryMessage", "severity", "type", "serverKey", "introductionDate",
        "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset")
      .containsExactly(ruleKey, message, IssueSeverity.BLOCKER, RuleType.BUG, "uuid", Instant.ofEpochMilli(123456789L), 5, 12, 5, 21);
    var flows = firstPublishedIssue.getFlows();
    assertThat(flows).hasSize(3);
    assertThat(flows.get(0).getLocations().get(0).getFileUri()).isEqualTo(fileUri);
    assertThat(flows.get(1).getLocations().get(0).getFileUri()).isEqualTo(fileUri);
    assertThat(flows.get(2).getLocations().get(0).getFileUri()).isEqualTo(fileUri);
    assertThat(flows.get(0).getLocations().get(0).getMessage()).isEqualTo("Duplication");
    assertThat(flows.get(1).getLocations().get(0).getMessage()).isEqualTo("Duplication");
    assertThat(flows.get(2).getLocations().get(0).getMessage()).isEqualTo("Duplication");
    var textRange1 = flows.get(0).getLocations().get(0).getTextRange();
    var textRange2 = flows.get(1).getLocations().get(0).getTextRange();
    var textRange3 = flows.get(2).getLocations().get(0).getTextRange();

    assertThat(textRange1.getStartLine()).isEqualTo(5);
    assertThat(textRange1.getStartLineOffset()).isEqualTo(12);
    assertThat(textRange1.getEndLine()).isEqualTo(5);
    assertThat(textRange1.getEndLineOffset()).isEqualTo(21);

    assertThat(textRange2.getStartLine()).isEqualTo(6);
    assertThat(textRange2.getStartLineOffset()).isEqualTo(12);
    assertThat(textRange2.getEndLine()).isEqualTo(6);
    assertThat(textRange2.getEndLineOffset()).isEqualTo(21);

    assertThat(textRange3.getStartLine()).isEqualTo(7);
    assertThat(textRange3.getStartLineOffset()).isEqualTo(12);
    assertThat(textRange3.getEndLine()).isEqualTo(7);
    assertThat(textRange3.getEndLineOffset()).isEqualTo(21);
  }

  @Disabled("https://sonarsource.atlassian.net/browse/SLCORE-873")
  @Test
  void it_should_track_line_level_server_issue_on_same_line(@TempDir Path baseDir) {
    var ideFilePath = "Foo.java";
    var filePath = createFile(baseDir, ideFilePath,
      "// FIXME foo bar\n" +
        "public class Foo {\n" +
        "}");
    var projectKey = "projectKey";
    var connectionId = "connectionId";
    var branchName = "main";
    var ruleKey = "java:S1134";
    var message = "Take the required action to fix the issue indicated by this comment.";

    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    var server = newSonarQubeServer("9.9")
      .withProject("projectKey", project -> project.withBranch("main", branch -> branch
        .withIssue("uuid", "java:S1134", message, "author", ideFilePath, "395d7a96efa8afd1b66ab6b680d0e637", Constants.Severity.BLOCKER,
          org.sonarsource.sonarlint.core.commons.RuleType.BUG,
          "OPEN", null, Instant.ofEpochMilli(123456789L), new TextRange(1, 0, 1, 16))))
      .withQualityProfile("qp", qualityProfile -> qualityProfile.withLanguage("java")
        .withActiveRule(ruleKey, activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR)))
      .start();
    backend = newBackend()
      .withSonarQubeConnection(connectionId, server,
        storage -> storage.withPlugin(TestPlugin.JAVA).withProject(projectKey,
          project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule(ruleKey, "MINOR"))
            .withMainBranch(branchName)))
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    backend.getConfigurationService()
      .didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
        new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID,
          new BindingConfigurationDto(connectionId, projectKey, true)))));

    var firstPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(firstPublishedIssue)
      .extracting("ruleKey", "primaryMessage", "severity", "type", "serverKey", "introductionDate",
        "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset")
      .containsExactly(ruleKey, message, IssueSeverity.BLOCKER, RuleType.BUG, "uuid", Instant.ofEpochMilli(123456789L), 1, 0, 1, 16);
  }

  @Disabled("https://sonarsource.atlassian.net/browse/SLCORE-873")
  @Test
  void it_should_track_line_level_server_issue_on_different_line(@TempDir Path baseDir) {
    var ideFilePath = "Foo.java";
    var filePath = createFile(baseDir, ideFilePath,
      "// FIXME foo bar\n" +
        "public class Foo {\n" +
        "}");
    var projectKey = "projectKey";
    var connectionId = "connectionId";
    var branchName = "main";
    var ruleKey = "java:S1134";
    var message = "Take the required action to fix the issue indicated by this comment.";

    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    var server = newSonarQubeServer("9.9")
      .withProject("projectKey", project -> project.withBranch("main", branch -> branch
        .withIssue("uuid", "java:S1134", message, "author", ideFilePath, "395d7a96efa8afd1b66ab6b680d0e637", Constants.Severity.BLOCKER,
          org.sonarsource.sonarlint.core.commons.RuleType.BUG,
          "OPEN", null, Instant.ofEpochMilli(123456789L), new TextRange(2, 0, 2, 16))))
      .withQualityProfile("qp", qualityProfile -> qualityProfile.withLanguage("java")
        .withActiveRule(ruleKey, activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR)))
      .start();
    backend = newBackend()
      .withSonarQubeConnection(connectionId, server,
        storage -> storage.withPlugin(TestPlugin.JAVA).withProject(projectKey,
          project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule(ruleKey, "MINOR"))
            .withMainBranch(branchName)))
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);

    backend.getConfigurationService()
      .didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
        new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID,
          new BindingConfigurationDto(connectionId, projectKey, true)))));
    var firstPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(firstPublishedIssue)
      .extracting("ruleKey", "primaryMessage", "severity", "type", "serverKey", "introductionDate",
        "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset")
      .containsExactly(ruleKey, message, IssueSeverity.BLOCKER, RuleType.BUG, "uuid", Instant.ofEpochMilli(123456789L), 1, 0, 1, 16);
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
      .extracting(RaisedIssueDto::getId, RaisedIssueDto::getIntroductionDate)
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
      .extracting(RaisedIssueDto::getIntroductionDate, as(INSTANT))
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
      .extracting(RaisedIssueDto::getId, RaisedIssueDto::getIntroductionDate)
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
      .extracting(RaisedIssueDto::getIntroductionDate, as(INSTANT))
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
      .extracting(RaisedIssueDto::getId, RaisedIssueDto::getIntroductionDate)
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
                .withMessage("Replace \"pom.version\" with \"project.version\".")
                .withFilePath("pom.xml")
                .withRuleKey("xml:S3421")
                .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "5507902b11374f7b2a6951d70635435d"))
                .withIntroductionDate(serverIssueIntroductionDate)))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .build(client);

    var publishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(publishedIssue)
      .extracting(RaisedIssueDto::getServerKey, RaisedIssueDto::getIntroductionDate)
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
                .withMessage("Replace \"pom.version\" with \"project.version\".")
                .withFilePath("pom.xml")
                .withRuleKey("xml:S3421")
                .withTextRange(new TextRangeWithHash(1, 2, 3, 4, "5507902b11374f7b2a6951d70635435d"))
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
          .build())
        .build()))));

    var newPublishedIssue = analyzeFileAndGetIssue(fileUri, client);

    assertThat(newPublishedIssue)
      .extracting(RaisedIssueDto::getId, RaisedIssueDto::getServerKey, RaisedIssueDto::getIntroductionDate)
      .containsExactly(firstPublishedIssue.getId(), "key", serverIssueIntroductionDate);
  }

  @Test
  void it_should_stream_issues(@TempDir Path baseDir) throws IOException, GitAPIException {
    var repository = createRepository(baseDir);
    var filePath = createFile(baseDir, "Foo.java", "a");
    var introductionDate = Instant.now().minus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    var commitDate = commitAtDate(repository, introductionDate, filePath.getFileName().toString());
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    var pluginPath = newSonarPlugin("java")
      .withSensor(IssueStreamingSensor.class)
      .withRulesDefinition(IssueStreamingRulesDefinition.class)
      .generate(baseDir);
    backend = newBackend()
      .withStandaloneEmbeddedPlugin(pluginPath)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .build(client);

    analyzeFileAndGetAllIssues(fileUri, client);

    ArgumentCaptor<Map<URI, List<RaisedIssueDto>>> intermediateIssuesByFileArgumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(client, times(2)).raiseIssues(eq(CONFIG_SCOPE_ID), intermediateIssuesByFileArgumentCaptor.capture(), eq(true), any());
    var allRaisedIntermediateIssuesByFile = intermediateIssuesByFileArgumentCaptor.getAllValues();
    var firstRaisedIntermediateIssuesByFile = allRaisedIntermediateIssuesByFile.get(0);
    assertThat(firstRaisedIntermediateIssuesByFile).containsOnlyKeys(fileUri);
    assertThat(firstRaisedIntermediateIssuesByFile.get(fileUri))
      .extracting(RaisedIssueDto::getPrimaryMessage, RaisedFindingDto::getIntroductionDate, RaisedFindingDto::isOnNewCode)
      .containsExactly(tuple("Issue 1", introductionDate, true));
    var secondRaisedIntermediateIssuesByFile = allRaisedIntermediateIssuesByFile.get(1);
    assertThat(secondRaisedIntermediateIssuesByFile).containsOnlyKeys(fileUri);
    assertThat(secondRaisedIntermediateIssuesByFile.get(fileUri))
      .extracting(RaisedIssueDto::getPrimaryMessage, RaisedFindingDto::getIntroductionDate, RaisedFindingDto::isOnNewCode)
      .containsExactly(tuple("Issue 1", introductionDate, true), tuple("Issue 2", commitDate.toInstant(), true));
    ArgumentCaptor<Map<URI, List<RaisedIssueDto>>> finalIssuesByFileArgumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(client).raiseIssues(eq(CONFIG_SCOPE_ID), finalIssuesByFileArgumentCaptor.capture(), eq(false), any());
    var finalIssuesByFile = finalIssuesByFileArgumentCaptor.getValue();
    assertThat(secondRaisedIntermediateIssuesByFile.keySet()).isEqualTo(finalIssuesByFile.keySet());
    assertThat(secondRaisedIntermediateIssuesByFile.get(fileUri)).usingRecursiveFieldByFieldElementComparatorIgnoringFields().isEqualTo(finalIssuesByFile.get(fileUri));
  }

  @Test
  void it_should_stream_issues_on_two_analyses_in_a_row(@TempDir Path baseDir) throws IOException, GitAPIException {
    var repository = createRepository(baseDir);
    var filePath = createFile(baseDir, "Foo.java", "a");
    var introductionDate = Instant.now().minus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    commitAtDate(repository, introductionDate, filePath.getFileName().toString());
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    var pluginPath = newSonarPlugin("java")
      .withSensor(IssueStreamingSensor.class)
      .withRulesDefinition(IssueStreamingRulesDefinition.class)
      .generate(baseDir);
    backend = newBackend()
      .withStandaloneEmbeddedPlugin(pluginPath)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .build(client);
    analyzeFileAndGetAllIssues(fileUri, client);
    reset(client);

    analyzeFileAndGetAllIssues(fileUri, client);

    ArgumentCaptor<Map<URI, List<RaisedIssueDto>>> intermediateIssuesByFileArgumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(client, times(2)).raiseIssues(eq(CONFIG_SCOPE_ID), intermediateIssuesByFileArgumentCaptor.capture(), eq(true), any());
    var allRaisedIntermediateIssuesByFile = intermediateIssuesByFileArgumentCaptor.getAllValues();
    var firstRaisedIntermediateIssuesByFile = allRaisedIntermediateIssuesByFile.get(0);
    assertThat(firstRaisedIntermediateIssuesByFile).containsOnlyKeys(fileUri);
    assertThat(firstRaisedIntermediateIssuesByFile.get(fileUri))
      .extracting(RaisedIssueDto::getPrimaryMessage, RaisedFindingDto::getIntroductionDate, RaisedFindingDto::isOnNewCode)
      .containsExactly(tuple("Issue 1", introductionDate, true));
    var secondRaisedIntermediateIssuesByFile = allRaisedIntermediateIssuesByFile.get(1);
    assertThat(secondRaisedIntermediateIssuesByFile).containsOnlyKeys(fileUri);
    assertThat(secondRaisedIntermediateIssuesByFile.get(fileUri))
      .extracting(RaisedIssueDto::getPrimaryMessage, RaisedFindingDto::getIntroductionDate, RaisedFindingDto::isOnNewCode)
      .containsExactly(tuple("Issue 1", introductionDate, true), tuple("Issue 2", introductionDate, true));
    ArgumentCaptor<Map<URI, List<RaisedIssueDto>>> finalIssuesByFileArgumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(client).raiseIssues(eq(CONFIG_SCOPE_ID), finalIssuesByFileArgumentCaptor.capture(), eq(false), any());
    var finalIssuesByFile = finalIssuesByFileArgumentCaptor.getValue();
    assertThat(secondRaisedIntermediateIssuesByFile.keySet()).isEqualTo(finalIssuesByFile.keySet());
    assertThat(secondRaisedIntermediateIssuesByFile.get(fileUri)).usingRecursiveFieldByFieldElementComparatorIgnoringFields().isEqualTo(finalIssuesByFile.get(fileUri));
  }

  @Test
  void it_should_include_a_file_without_issues_when_raising_issues(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "Foo.java", "");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null)))
      .build();
    var pluginPath = newSonarPlugin("java")
      .generate(baseDir);
    backend = newBackend()
      .withStandaloneEmbeddedPlugin(pluginPath)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .build(client);
    backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri), Map.of(), true, System.currentTimeMillis()))
      .join();

    verify(client, never()).raiseIssues(eq(CONFIG_SCOPE_ID), any(), eq(true), any());
    verify(client).raiseIssues(eq(CONFIG_SCOPE_ID), eq(Map.of(fileUri, List.of())), eq(false), any());
  }

  private List<RaisedIssueDto> analyzeFileAndGetAllIssuesOfRule(URI fileUri, SonarLintRpcClientDelegate client, String ruleKey) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), true, System.currentTimeMillis()))
      .join();
    var publishedIssuesByFile = getPublishedIssues(client, analysisId);
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    assertThat(publishedIssuesByFile).containsOnlyKeys(fileUri);
    var raisedIssues = publishedIssuesByFile.get(fileUri);
    return raisedIssues.stream().filter(ri -> ri.getRuleKey().equals(ruleKey)).collect(Collectors.toList());
  }

  private List<RaisedIssueDto> analyzeFileAndGetAllIssues(URI fileUri, SonarLintRpcClientDelegate client) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), true, System.currentTimeMillis())).join();
    var publishedIssuesByFile = getPublishedIssues(client, analysisId);
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    assertThat(publishedIssuesByFile).containsOnlyKeys(fileUri);
    return publishedIssuesByFile.get(fileUri);
  }

  private RaisedIssueDto analyzeFileAndGetIssue(URI fileUri, SonarLintRpcClientDelegate client) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), true, System.currentTimeMillis())).join();
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

  private Map<URI, List<RaisedIssueDto>> getPublishedIssues(SonarLintRpcClientDelegate client, UUID analysisId) {
    ArgumentCaptor<Map<URI, List<RaisedIssueDto>>> trackedIssuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(client, timeout(300)).raiseIssues(eq(CONFIG_SCOPE_ID), trackedIssuesCaptor.capture(), eq(false), eq(analysisId));
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

  private static void changeFileContent(Path folderPath, String fileName, String content) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.writeString(filePath, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
