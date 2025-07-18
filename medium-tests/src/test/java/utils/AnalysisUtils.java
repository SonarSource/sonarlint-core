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
package utils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class AnalysisUtils {

  private AnalysisUtils() {
    // util
  }

  public static Path createFile(Path folderPath, String fileName, String content) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.writeString(filePath, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return filePath;
  }

  public static RaisedIssueDto analyzeFileAndGetIssue(URI fileUri, SonarLintBackendFixture.FakeSonarLintRpcClient client, SonarLintTestRpcServer backend, String scopeId) {
    var raisedIssues = analyzeFileAndGetIssues(fileUri, client, backend, scopeId);
    return raisedIssues.get(0);
  }

  public static List<RaisedIssueDto> analyzeFileAndGetIssues(URI fileUri, SonarLintBackendFixture.FakeSonarLintRpcClient client, SonarLintTestRpcServer backend, String scopeId) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(scopeId, analysisId, List.of(fileUri), Map.of(), false, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(scopeId)).isNotEmpty());
    return client.getRaisedIssuesForScopeId(scopeId).get(fileUri);
  }

  public static Map<URI, List<RaisedIssueDto>> analyzeFilesAndGetIssuesAsMap(List<URI> files, SonarLintBackendFixture.FakeSonarLintRpcClient client, SonarLintTestRpcServer backend, String scopeId) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(scopeId, analysisId, files, Map.of(), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(scopeId)).isNotEmpty());
    return client.getRaisedIssuesForScopeId(scopeId);
  }

  public static void analyzeFilesAndVerifyNoIssues(List<URI> files, SonarLintBackendFixture.FakeSonarLintRpcClient client, SonarLintTestRpcServer backend, String scopeId) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(scopeId, analysisId, files, Map.of(), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().during(1, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(scopeId)).isEmpty());
  }

  public static void analyzeFileAndGetHotspots(URI fileUri, SonarLintBackendFixture.FakeSonarLintRpcClient client, SonarLintTestRpcServer backend, String scopeId) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(scopeId, analysisId, List.of(fileUri), Map.of(), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().atMost(40, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedHotspotsForScopeIdAsList(scopeId)).isNotEmpty());
    assertThat(client.getRaisedHotspotsForScopeId(scopeId)).containsOnlyKeys(fileUri);
  }

  public static Map<URI, List<RaisedIssueDto>> getPublishedIssues(SonarLintBackendFixture.FakeSonarLintRpcClient client, String scopeId) {
    await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(scopeId)).isNotEmpty());
    return client.getRaisedIssuesForScopeId(scopeId);
  }

  public static void waitForRaisedIssues(SonarLintBackendFixture.FakeSonarLintRpcClient client, String scopeId) {
    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(scopeId)).isNotEmpty());
  }

  public static void waitForAnalysisReady(SonarLintBackendFixture.FakeSonarLintRpcClient client, String scopeId) {
    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.isAnalysisReadyForScope(scopeId)).isTrue());
  }

  public static void editFile(Path folderPath, String fileName, String content) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.writeString(filePath, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void removeFile(Path folderPath, String fileName) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.deleteIfExists(filePath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static List<RaisedIssueDto> awaitRaisedIssuesNotification(SonarLintBackendFixture.FakeSonarLintRpcClient client, String configScopeId) {
    waitForRaisedIssues(client, configScopeId);
    return client.getRaisedIssuesForScopeIdAsList(configScopeId);
  }
}
