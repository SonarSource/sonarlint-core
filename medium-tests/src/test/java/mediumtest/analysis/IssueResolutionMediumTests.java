/*
 * SonarLint Core - Medium Tests
 * Copyright (C) SonarSource Sàrl
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
package mediumtest.analysis;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mediumtest.analysis.sensor.IssueResolutionRulesDefinition;
import mediumtest.analysis.sensor.IssueResolutionSensor;
import mediumtest.analysis.sensor.IssueThenResolutionSensor;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.test.utils.plugins.SonarPluginBuilder.newSonarPlugin;
import static utils.AnalysisUtils.analyzeFileAndGetIssues;
import static utils.AnalysisUtils.createFile;

class IssueResolutionMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";

  @SonarLintTest
  void it_should_not_raise_issues_resolved_by_sonar_resolve(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "Foo.java",
      """
        class Foo {
          int unused; // sonar-resolve repo:rule "not used on purpose"
          int unused;
        }
        """);
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var pluginPath = newSonarPlugin("java")
      .withSensor(IssueResolutionSensor.class)
      .withRulesDefinition(IssueResolutionRulesDefinition.class)
      .generate(baseDir);
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPlugin(pluginPath)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start(client);

    var issues = analyzeFileAndGetIssues(fileUri, client, backend, CONFIG_SCOPE_ID);

    assertThat(issues)
      .extracting(RaisedIssueDto::getRuleKey, i -> i.getTextRange().getStartLine())
      .containsExactly(tuple("repo:rule", 3));
  }

  @SonarLintTest
  void it_should_not_raise_issues_resolved_after_they_were_reported(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "Foo.java",
      """
        class Foo {
          int unused; // sonar-resolve repo:rule "not used on purpose"
          int unused;
        }
        """);
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var pluginPath = newSonarPlugin("java")
      .withSensor(IssueThenResolutionSensor.class)
      .withRulesDefinition(IssueResolutionRulesDefinition.class)
      .generate(baseDir);
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPlugin(pluginPath)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start(client);

    var issues = analyzeFileAndGetIssues(fileUri, client, backend, CONFIG_SCOPE_ID);

    assertThat(issues)
      .extracting(RaisedIssueDto::getRuleKey, i -> i.getTextRange().getStartLine())
      .containsExactly(tuple("repo:rule", 3));
  }

  @SonarLintTest
  void it_should_retract_streamed_issues_on_the_next_publication(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "Foo.java",
      """
        class Foo {
          int unused; // sonar-resolve repo:rule "not used on purpose"
          int unused;
        }
        """);
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var pluginPath = newSonarPlugin("java")
      .withSensor(IssueThenResolutionSensor.class)
      .withRulesDefinition(IssueResolutionRulesDefinition.class)
      .generate(baseDir);
    var backend = harness.newBackend()
      .withBackendCapability(BackendCapability.ISSUE_STREAMING)
      .withStandaloneEmbeddedPlugin(pluginPath)
      .withEnabledLanguageInStandaloneMode(Language.JAVA)
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .start(client);

    backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri), Map.of(), false))
      .join();

    ArgumentCaptor<Map<URI, List<RaisedIssueDto>>> intermediateIssuesByFileArgumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(client, timeout(5000).times(2)).raiseIssues(eq(CONFIG_SCOPE_ID), intermediateIssuesByFileArgumentCaptor.capture(), eq(true), any());
    var allRaisedIntermediateIssuesByFile = intermediateIssuesByFileArgumentCaptor.getAllValues();
    var firstRaisedIntermediateIssuesByFile = allRaisedIntermediateIssuesByFile.get(0);
    assertThat(firstRaisedIntermediateIssuesByFile).containsOnlyKeys(fileUri);
    assertThat(firstRaisedIntermediateIssuesByFile.get(fileUri))
      .extracting(i -> i.getTextRange().getStartLine())
      .containsExactly(2, 3);
    var secondRaisedIntermediateIssuesByFile = allRaisedIntermediateIssuesByFile.get(1);
    assertThat(secondRaisedIntermediateIssuesByFile).containsOnlyKeys(fileUri);
    assertThat(secondRaisedIntermediateIssuesByFile.get(fileUri))
      .extracting(i -> i.getTextRange().getStartLine())
      .containsExactly(3);
    ArgumentCaptor<Map<URI, List<RaisedIssueDto>>> finalIssuesByFileArgumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(client, timeout(5000)).raiseIssues(eq(CONFIG_SCOPE_ID), finalIssuesByFileArgumentCaptor.capture(), eq(false), any());
    var finalIssuesByFile = finalIssuesByFileArgumentCaptor.getValue();
    assertThat(finalIssuesByFile.get(fileUri))
      .extracting(i -> i.getTextRange().getStartLine())
      .containsExactly(3);
  }
}
