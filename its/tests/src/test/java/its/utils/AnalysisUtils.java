/*
 * SonarLint Core - ITs - Tests
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
package its.utils;

import its.MockSonarLintRpcClientDelegate;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class AnalysisUtils {

  private AnalysisUtils() {
    // utility class
  }

  public static List<RaisedIssueDto> analyzeAndAwaitIssues(SonarLintRpcServer backend, MockSonarLintRpcClientDelegate client, String configScopeId, Path baseDir,
    String filePathStr, String... properties) {
    var filePath = baseDir.resolve(filePathStr);
    var fileUri = filePath.toAbsolutePath().toUri();
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(
      List.of(new ClientFileDto(fileUri, Path.of(filePathStr), configScopeId, false, null, filePath.toAbsolutePath(), null, null, true)),
      List.of(), List.of()));
    return analyzeAndAwaitIssues(backend, client, configScopeId, List.of(fileUri), toMap(properties));
  }

  public static List<RaisedIssueDto> analyzeAndAwaitIssues(SonarLintRpcServer backend, MockSonarLintRpcClientDelegate client, String configScopeId, List<URI> fileUris,
    Map<String, String> extraProperties) {
    var response = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(configScopeId, UUID.randomUUID(), fileUris, extraProperties, true)).join();
    assertThat(response.getFailedAnalysisFiles()).isEmpty();
    var issues = await().until(() -> client.takeRaisedIssues(configScopeId), Objects::nonNull);
    return issues.values().stream().flatMap(List::stream).toList();
  }

  public static List<RaisedHotspotDto> analyzeAndAwaitHotspots(SonarLintRpcServer backend, MockSonarLintRpcClientDelegate client, String configScopeId, Path baseDir,
    String filePathStr, String... properties) {
    var filePath = baseDir.resolve(filePathStr);
    var fileUri = filePath.toAbsolutePath().toUri();
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(
      List.of(),
      List.of(new ClientFileDto(fileUri, Path.of(filePathStr), configScopeId, false, null, filePath.toAbsolutePath(), null, null, true)),
      List.of()));
    return analyzeAndAwaitHotspots(backend, client, configScopeId, List.of(fileUri), toMap(properties));
  }

  public static List<RaisedHotspotDto> analyzeAndAwaitHotspots(SonarLintRpcServer backend, MockSonarLintRpcClientDelegate client, String configScopeId, List<URI> fileUris,
    Map<String, String> extraProperties) {
    var response = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(configScopeId, UUID.randomUUID(), fileUris, extraProperties, true)).join();
    assertThat(response.getFailedAnalysisFiles()).isEmpty();
    var hotspots = await().until(() -> client.takeRaisedHotspots(configScopeId), Objects::nonNull);
    return hotspots.values().stream().flatMap(List::stream).toList();
  }

  private static Map<String, String> toMap(String[] keyValues) {
    var map = new HashMap<String, String>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(keyValues[i], keyValues[i + 1]);
    }
    return map;
  }
}
