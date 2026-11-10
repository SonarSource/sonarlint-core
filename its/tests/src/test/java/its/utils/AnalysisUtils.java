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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
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

  public static void registerFiles(SonarLintRpcServer backend, String configScopeId, Path baseDir, String... relativePaths) {
    var files = Arrays.stream(relativePaths).map(relativePath -> toClientFile(configScopeId, baseDir, relativePath)).toList();
    backend.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(files, List.of(), List.of()));
  }

  public static List<RaisedIssueDto> analyzeUntilIssuesAreRaised(SonarLintRpcServer backend, MockSonarLintRpcClientDelegate client, String configScopeId, Path file) {
    return await().atMost(2, TimeUnit.MINUTES).pollInterval(4, TimeUnit.SECONDS).ignoreExceptions()
      .until(() -> analyzeFile(backend, client, configScopeId, file), issues -> !issues.isEmpty());
  }

  public static List<RaisedIssueDto> analyzeFile(SonarLintRpcServer backend, MockSonarLintRpcClientDelegate client, String configScopeId, Path file) {
    return analyzeAndAwaitIssues(backend, client, configScopeId, List.of(file.toUri()), Map.of());
  }

  public static int startLine(RaisedIssueDto issue) {
    return issue.getTextRange().getStartLine();
  }

  private static ClientFileDto toClientFile(String configScopeId, Path baseDir, String relativePath) {
    var filePath = baseDir.resolve(relativePath).toAbsolutePath();
    return new ClientFileDto(filePath.toUri(), Path.of(relativePath), configScopeId, false, null, filePath, null, null, true);
  }

  public static List<RaisedIssueDto> analyzeAndAwaitIssues(SonarLintRpcServer backend, MockSonarLintRpcClientDelegate client, String configScopeId, List<URI> fileUris,
    Map<String, String> extraProperties) {
    var response = backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(configScopeId, UUID.randomUUID(), fileUris, extraProperties, true)).join();
    assertThat(response.getFailedAnalysisFiles()).isEmpty();
    var issues = await().until(() -> client.takeRaisedIssues(configScopeId), Objects::nonNull);
    return issues.values().stream().flatMap(List::stream).toList();
  }

  private static Map<String, String> toMap(String[] keyValues) {
    var map = new HashMap<String, String>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(keyValues[i], keyValues[i + 1]);
    }
    return map;
  }
}
