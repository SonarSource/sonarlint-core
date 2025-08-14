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
package mediumtest.file;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.scanner.protocol.Constants;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidOpenFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.GetFilesStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.AnalysisUtils;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static utils.AnalysisUtils.getPublishedIssues;

class ClientFileExclusionsMediumTests {
  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";

  @SonarLintTest
  void it_should_not_analyze_excluded_file_on_open(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createXmlFile(baseDir);
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .withFileExclusions(CONFIG_SCOPE_ID, Set.of("**/*.xml"))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .start(client);

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));

    await().pollDelay(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS)
      .untilAsserted(() -> verify(client).raiseIssues(eq(CONFIG_SCOPE_ID), any(), eq(false), any()));
    assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isEmpty();
  }

  @SonarLintTest
  void it_should_analyze_not_excluded_file_on_open(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createXmlFile(baseDir);
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .withFileExclusions(CONFIG_SCOPE_ID, Set.of("**/*.java"))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .start(client);

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));

    var publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues)
        .extracting(RaisedIssueDto::getPrimaryMessage)
        .containsExactly("Replace \"pom.version\" with \"project.version\"."));
  }

  @SonarLintTest
  void it_should_not_analyze_non_user_defined_file_on_open(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createXmlFile(baseDir);
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, false)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .start(client);

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));

    await().pollDelay(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS)
      .untilAsserted(() -> verify(client).raiseIssues(eq(CONFIG_SCOPE_ID), any(), eq(false), any()));
    assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isEmpty();
  }

  @SonarLintTest
  void it_should_analyze_user_defined_file_on_open(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createXmlFile(baseDir);
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .start(client);

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));

    var publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
    assertThat(publishedIssues)
      .containsOnlyKeys(fileUri)
      .hasEntrySatisfying(fileUri, issues -> assertThat(issues)
        .extracting(RaisedIssueDto::getPrimaryMessage)
        .containsExactly("Replace \"pom.version\" with \"project.version\"."));
  }

  @SonarLintTest
  void it_should_not_exclude_client_defined_file_exclusion_in_connected_mode(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var ideFilePath = "Foo.java";
    var filePath = AnalysisUtils.createFile(baseDir, ideFilePath,
      """
        // FIXME foo bar
        public class Foo {
        }""");
    var projectKey = "projectKey";
    var connectionId = "connectionId";
    var branchName = "main";
    var ruleKey = "java:S1134";
    var message = "Take the required action to fix the issue indicated by this comment.";

    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .withFileExclusions(CONFIG_SCOPE_ID, Set.of("**/*.java"))
      .build();
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey", project -> project.withBranch("main", branch -> branch
        .withIssue("uuid", "java:S1134", message, "author", ideFilePath, "395d7a96efa8afd1b66ab6b680d0e637", Constants.Severity.BLOCKER,
          org.sonarsource.sonarlint.core.commons.RuleType.BUG,
          "OPEN", null, Instant.ofEpochMilli(123456789L), new TextRange(2, 0, 2, 16))))
      .withQualityProfile("qp", qualityProfile -> qualityProfile.withLanguage("java")
        .withActiveRule(ruleKey, activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR)))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(connectionId, server,
        storage -> storage.withPlugin(TestPlugin.JAVA).withProject(projectKey,
          project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule(ruleKey, "MINOR"))
            .withMainBranch(branchName)))
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .start(client);

    backend.getConfigurationService()
      .didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
        new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID,
          new BindingConfigurationDto(connectionId, projectKey, true)))));

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));

    var publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
    assertThat(publishedIssues).isNotEmpty();
  }

  @SonarLintTest
  void it_should_exclude_non_user_defined_files_in_connected_mode(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var ideFilePath = "Foo.java";
    var filePath = AnalysisUtils.createFile(baseDir, ideFilePath,
      """
        // FIXME foo bar
        public class Foo {
        }""");
    var projectKey = "projectKey";
    var connectionId = "connectionId";
    var branchName = "main";
    var ruleKey = "java:S1134";
    var message = "Take the required action to fix the issue indicated by this comment.";

    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, false)))
      .build();
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey", project -> project.withBranch("main", branch -> branch
        .withIssue("uuid", "java:S1134", message, "author", ideFilePath, "395d7a96efa8afd1b66ab6b680d0e637", Constants.Severity.BLOCKER,
          org.sonarsource.sonarlint.core.commons.RuleType.BUG,
          "OPEN", null, Instant.ofEpochMilli(123456789L), new TextRange(2, 0, 2, 16))))
      .withQualityProfile("qp", qualityProfile -> qualityProfile.withLanguage("java")
        .withActiveRule(ruleKey, activeRule -> activeRule.withSeverity(IssueSeverity.MAJOR)))
      .start();
    var backend = harness.newBackend()
      .withSonarQubeConnection(connectionId, server,
        storage -> storage.withPlugin(TestPlugin.JAVA).withProject(projectKey,
          project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule(ruleKey, "MINOR"))
            .withMainBranch(branchName)))
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .start(client);

    backend.getConfigurationService()
      .didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(
        new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID,
          new BindingConfigurationDto(connectionId, projectKey, true)))));

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));

    await().pollDelay(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS)
      .untilAsserted(() -> verify(client).raiseIssues(eq(CONFIG_SCOPE_ID), any(), eq(false), any()));
    assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isEmpty();
  }

  @SonarLintTest
  void it_should_include_client_exclusions_when_getting_file_status(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createXmlFile(baseDir);
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .withFileExclusions(CONFIG_SCOPE_ID, Set.of("**/*.xml"))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .start(client);

    var future = backend.getFileService().getFilesStatus(new GetFilesStatusParams(Map.of(CONFIG_SCOPE_ID, List.of(fileUri))));

    assertThat(future).succeedsWithin(5, TimeUnit.SECONDS);
    assertThat(future.join().getFileStatuses().entrySet())
      .extracting(Map.Entry::getKey, e -> e.getValue().isExcluded())
      .containsExactlyInAnyOrder(
        tuple(fileUri, true));
  }

  private static Path createXmlFile(Path baseDir) {
    return AnalysisUtils.createFile(baseDir, "pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.foo</groupId>
          <artifactId>bar</artifactId>
          <version>${pom.version}</version>
        </project>""");
  }
}
