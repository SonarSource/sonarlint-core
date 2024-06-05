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
package testutils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

public class AnalysisUtils {

  private AnalysisUtils() {
    // util
  }

  public static Path createFile(Path folderPath, String fileName, String content) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.createDirectories(filePath.getParent());
      Files.writeString(filePath, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return filePath;
  }

  public static RaisedIssueDto analyzeFileAndGetIssue(URI fileUri, SonarLintRpcClientDelegate client, SonarLintRpcServer backend, String scopeId) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(scopeId, analysisId, List.of(fileUri), Map.of(), true, System.currentTimeMillis()))
      .join();
    var publishedIssuesByFile = getPublishedIssues(client, analysisId, scopeId);
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    assertThat(publishedIssuesByFile).containsOnlyKeys(fileUri);
    var publishedIssues = publishedIssuesByFile.get(fileUri);
    assertThat(publishedIssues).hasSize(1);
    return publishedIssues.get(0);
  }

  public static Map<URI, List<RaisedIssueDto>> analyzeAndGetIssuesByFile(SonarLintRpcServer backend, SonarLintRpcClientDelegate client, String scopeId, URI... fileUri) {
    return analyzeAndGetIssuesByFile(backend, client, scopeId, Map.of(), fileUri);
  }

  public static Map<URI, List<RaisedIssueDto>> analyzeAndGetIssuesByFile(SonarLintRpcServer backend, SonarLintRpcClientDelegate client, String scopeId,
    Map<String, String> properties, URI... fileUri) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(scopeId, analysisId, Arrays.asList(fileUri), properties, true, System.currentTimeMillis()))
      .join();
    var publishedIssuesByFile = getPublishedIssues(client, analysisId, scopeId);
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    return publishedIssuesByFile;
  }

  public static RaisedHotspotDto analyzeFileAndGetHotspot(URI fileUri, SonarLintRpcClientDelegate client, SonarLintRpcServer backend, String scopeId) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(scopeId, analysisId, List.of(fileUri), Map.of(), true, System.currentTimeMillis()))
      .join();
    var publishedHotspotsByFile = getPublishedHotspots(client, analysisId, scopeId);
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    assertThat(publishedHotspotsByFile).containsOnlyKeys(fileUri);
    var publishedHotspots = publishedHotspotsByFile.get(fileUri);
    assertThat(publishedHotspots).hasSize(1);
    return publishedHotspots.get(0);
  }

  public static Map<URI, List<RaisedHotspotDto>> analyzeAndGetAllHotspotsByFile(URI fileUri, SonarLintRpcClientDelegate client, SonarLintRpcServer backend, String scopeId) {
    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(scopeId, analysisId, List.of(fileUri), Map.of(), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    return getPublishedHotspots(client, analysisId, scopeId);
  }

  private static Map<URI, List<RaisedIssueDto>> getPublishedIssues(SonarLintRpcClientDelegate client, UUID analysisId, String scopeId) {
    ArgumentCaptor<Map<URI, List<RaisedIssueDto>>> trackedIssuesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(client, timeout(300)).raiseIssues(eq(scopeId), trackedIssuesCaptor.capture(), eq(false), eq(analysisId));
    return trackedIssuesCaptor.getValue();
  }

  private static Map<URI, List<RaisedHotspotDto>> getPublishedHotspots(SonarLintRpcClientDelegate client, UUID analysisId, String scopeId) {
    ArgumentCaptor<Map<URI, List<RaisedHotspotDto>>> trackedHotspotsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(client, timeout(300)).raiseHotspots(eq(scopeId), trackedHotspotsCaptor.capture(), eq(false), eq(analysisId));
    return trackedHotspotsCaptor.getValue();
  }
}
