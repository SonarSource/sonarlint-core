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
package mediumtest.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import mediumtest.fixtures.SonarLintTestRpcServer;
import mediumtest.fixtures.TestPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AnalysisMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private SonarLintTestRpcServer backend;

  @AfterEach
  void stop() {
    if (backend != null) {
      backend.shutdown();
    }
  }

  @Test
  void it_should_skip_analysis_if_no_file_provided(@TempDir Path tempDir) {
    var client = newFakeClient().build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .build(client);

    var result = backend.getAnalysisService()
      .analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, List.of(tempDir.resolve("File.java").toUri()), Map.of(), System.currentTimeMillis())).join();

    assertThat(result).isNotNull();
    verify(client, never()).didRaiseIssue(eq(CONFIG_SCOPE_ID), any());
  }

  @Test
  void it_should_analyze_xml_file_in_standalone_mode(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<project>\n"
        + "  <modelVersion>4.0.0</modelVersion>\n"
        + "  <groupId>com.foo</groupId>\n"
        + "  <artifactId>bar</artifactId>\n"
        + "  <version>${pom.version}</version>\n"
        + "</project>");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.XML)
      .build(client);

    var result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, List.of(fileUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result).isNotNull();
    var rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client).didRaiseIssue(eq(CONFIG_SCOPE_ID), rawIssueCaptor.capture());
    var rawIssue = rawIssueCaptor.getValue();
    assertThat(rawIssue.getSeverity()).isEqualTo(IssueSeverity.MINOR);
    assertThat(rawIssue.getType()).isEqualTo(RuleType.CODE_SMELL);
    assertThat(rawIssue.getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.CONVENTIONAL);
    assertThat(rawIssue.getImpacts()).isEqualTo(Map.of(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.LOW));
    assertThat(rawIssue.getRuleKey()).isEqualTo("xml:S3421");
    assertThat(rawIssue.getPrimaryMessage()).isEqualTo("Replace \"pom.version\" with \"project.version\".");
    assertThat(rawIssue.getFileUri()).isEqualTo(fileUri);
    assertThat(rawIssue.getFlows()).isEmpty();
    assertThat(rawIssue.getQuickFixes()).isEmpty();
    assertThat(rawIssue.getTextRange()).usingRecursiveComparison().isEqualTo(new TextRangeDto(6, 11, 6, 25));
    assertThat(rawIssue.getRuleDescriptionContextKey()).isNull();
    assertThat(rawIssue.getVulnerabilityProbability()).isNull();
  }

  @Test
  void it_should_analyze_xml_file_in_connected_mode(@TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<project>\n"
        + "  <modelVersion>4.0.0</modelVersion>\n"
        + "  <groupId>com.foo</groupId>\n"
        + "  <artifactId>bar</artifactId>\n"
        + "  <version>${pom.version}</version>\n"
        + "</project>");
    var fileUri = filePath.toUri();
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null)))
      .build();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", storage -> storage.withPlugin(TestPlugin.XML).withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "BLOCKER"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .build(client);

    var result = backend.getAnalysisService().analyzeFiles(new AnalyzeFilesParams(CONFIG_SCOPE_ID, List.of(fileUri), Map.of(), System.currentTimeMillis())).join();

    assertThat(result).isNotNull();
    var rawIssueCaptor = ArgumentCaptor.forClass(RawIssueDto.class);
    verify(client).didRaiseIssue(eq(CONFIG_SCOPE_ID), rawIssueCaptor.capture());
    var rawIssue = rawIssueCaptor.getValue();
    assertThat(rawIssue.getSeverity()).isEqualTo(IssueSeverity.BLOCKER);
    assertThat(rawIssue.getRuleKey()).isEqualTo("xml:S3421");
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
