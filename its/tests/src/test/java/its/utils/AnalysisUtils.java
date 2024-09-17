/*
 * SonarLint Core - ITs - Tests
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
package its.utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import its.MockSonarLintRpcClientDelegate;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;

import static org.assertj.core.api.Assertions.assertThat;

public class AnalysisUtils {

  private AnalysisUtils() {
    // utility class
  }

  public static List<RaisedIssueDto> analyzeAndGetIssues(String projectKey, String fileName, String configScopeId, SonarLintRpcServer backend, SonarLintRpcClientDelegate client, String ... properties) {
    final var baseDir = Paths.get("projects/" + projectKey).toAbsolutePath();
    final var filePath = baseDir.resolve(fileName);
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(),
      List.of(new ClientFileDto(filePath.toUri(), Path.of(fileName), configScopeId, false, null, filePath, null, null, true))));

    var analyzeResponse = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(configScopeId, UUID.randomUUID(), List.of(filePath.toUri()), toMap(properties), true, System.currentTimeMillis())
    ).join();

    assertThat(analyzeResponse.getFailedAnalysisFiles()).isEmpty();
    var raisedIssues = ((MockSonarLintRpcClientDelegate) client).getRaisedIssues(configScopeId);
    ((MockSonarLintRpcClientDelegate) client).getRaisedIssues().clear();
    return raisedIssues != null ? raisedIssues.values().stream().flatMap(List::stream).collect(Collectors.toList()) : List.of();
  }

  public static List<RaisedHotspotDto> analyzeAndGetHotspots(String projectKey, String fileName, String configScopeId, SonarLintRpcServer backend, SonarLintRpcClientDelegate client,  String ... properties) {
    final var baseDir = Paths.get("projects/" + projectKey).toAbsolutePath();
    final var filePath = baseDir.resolve(fileName);
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(),
      List.of(new ClientFileDto(filePath.toUri(), Path.of(fileName), configScopeId, false, null, filePath, null, null, true))));

    var analyzeResponse = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(configScopeId, UUID.randomUUID(), List.of(filePath.toUri()), toMap(properties), true, System.currentTimeMillis())
    ).join();

    assertThat(analyzeResponse.getFailedAnalysisFiles()).isEmpty();
    var raisedHotspots = ((MockSonarLintRpcClientDelegate) client).getRaisedHotspots(configScopeId);
    ((MockSonarLintRpcClientDelegate) client).getRaisedIssues().clear();
    return raisedHotspots != null ? raisedHotspots.values().stream().flatMap(List::stream).collect(Collectors.toList()) : List.of();
  }

  static Map<String, String> toMap(String[] keyValues) {
    Preconditions.checkArgument(keyValues.length % 2 == 0, "Must be an even number of key/values");
    Map<String, String> map = Maps.newHashMap();
    var index = 0;
    while (index < keyValues.length) {
      var key = keyValues[index++];
      var value = keyValues[index++];
      map.put(key, value);
    }
    return map;
  }

}
