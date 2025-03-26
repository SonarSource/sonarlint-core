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
package mediumtest.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.commons.LogTestStartAndEnd;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.OnDiskTestClientInputFile;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.verify;
import static utils.AnalysisUtils.waitForRaisedIssues;

@ExtendWith(LogTestStartAndEnd.class)
class AnalysisCharsetMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";

  @SonarLintTest
  void it_should_skip_utf8_bom_when_reading_from_disk(SonarLintTestHarness harness, @TempDir Path baseDir) throws IOException {
    var filePath = baseDir.resolve("file.js");
    Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/file-with-utf8-bom.js")), filePath);
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVASCRIPT)
      .start(client);
    var analysisId = UUID.randomUUID();

    var result = backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileUri), Map.of(), false, System.currentTimeMillis())).join();

    assertThat(result.getFailedAnalysisFiles()).isEmpty();
    var raisedIssueDto = awaitRaisedIssuesNotification(client, CONFIG_SCOPE_ID).get(0);
    assertThat(raisedIssueDto.getSeverityMode().isRight()).isTrue();
    assertThat(raisedIssueDto.getSeverityMode().getRight().getCleanCodeAttribute()).isEqualTo(CleanCodeAttribute.CONVENTIONAL);
    assertThat(raisedIssueDto.getSeverityMode().getRight().getImpacts())
      .extracting(ImpactDto::getSoftwareQuality, ImpactDto::getImpactSeverity)
      .containsExactly(tuple(SoftwareQuality.RELIABILITY, ImpactSeverity.MEDIUM));
    assertThat(raisedIssueDto.getRuleKey()).isEqualTo("javascript:S905");
    assertThat(raisedIssueDto.getPrimaryMessage()).isEqualTo("Expected an assignment or function call and instead saw an expression.");
    assertThat(raisedIssueDto.getFlows()).isEmpty();
    assertThat(raisedIssueDto.getQuickFixes()).isEmpty();
    assertThat(raisedIssueDto.getTextRange()).usingRecursiveComparison().isEqualTo(new TextRangeDto(1, 1, 23, 8));
    assertThat(raisedIssueDto.getRuleDescriptionContextKey()).isNull();
  }

  private ClientInputFile prepareInputFile(String relativePath, String content, final boolean isTest, Charset encoding,
    @Nullable SonarLanguage language, Path baseDir) throws IOException {
    final var file = new File(baseDir.toFile(), relativePath);
    FileUtils.write(file, content, encoding);
    return new OnDiskTestClientInputFile(file.toPath(), relativePath, isTest, encoding, language);
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

  private static void editFile(Path folderPath, String fileName, String content) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.writeString(filePath, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void removeFile(Path folderPath, String fileName) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.deleteIfExists(filePath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<RaisedIssueDto> awaitRaisedIssuesNotification(SonarLintBackendFixture.FakeSonarLintRpcClient client, String configScopeId) {
    waitForRaisedIssues(client, configScopeId);
    return client.getRaisedIssuesForScopeIdAsList(configScopeId);
  }
}
