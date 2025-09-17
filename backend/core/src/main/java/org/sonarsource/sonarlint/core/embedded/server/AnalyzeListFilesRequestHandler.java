/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.sonarsource.sonarlint.core.analysis.api.TriggerType;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;

public class AnalyzeListFilesRequestHandler implements HttpRequestHandler {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final AnalysisService analysisService;
  private final ClientFileSystemService clientFileSystemService;
  private final Gson gson = new Gson();

  public AnalyzeListFilesRequestHandler(AnalysisService analysisService, ClientFileSystemService clientFileSystemService) {
    this.analysisService = analysisService;
    this.clientFileSystemService = clientFileSystemService;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext httpContext) throws HttpException, IOException {
    if (!Method.POST.isSame(request.getMethod())) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }

    AnalysisRequest analysisRequest;
    try {
      var requestBody = EntityUtils.toString(request.getEntity(), "UTF-8");
      analysisRequest = gson.fromJson(requestBody, AnalysisRequest.class);
    } catch (Exception e) {
      LOG.warn("Failed to parse MCP analysis request", e);
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }

    if (analysisRequest == null || analysisRequest.fileList == null || analysisRequest.fileList.isEmpty()) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }

    response.setEntity(new StringEntity(new Gson().toJson(analyze(analysisRequest)), ContentType.APPLICATION_JSON));
  }

  private AnalysisResult analyze(AnalysisRequest request) {
    var cancelMonitor = new SonarLintCancelMonitor();
    var filePaths = request.fileList.stream()
      .map(path -> URI.create("file://" + path))
      .collect(Collectors.toSet());

    LOG.debug("Analyzing {} files: {}", filePaths.size(), filePaths);

    var filesByScope = clientFileSystemService.groupFilesByConfigScope(filePaths);
    LOG.debug("Files grouped by scope: {}", filesByScope);

    var allIssues = filesByScope.entrySet().stream().flatMap(entry -> {
      var configScopeId = entry.getKey();
      var files = entry.getValue();
      LOG.debug("Analyzing {} files in scope '{}': {}", files.size(), configScopeId, files);
      
      try {
        var analysisResults = analysisService.scheduleAnalysis(configScopeId, UUID.randomUUID(), files, Collections.emptyMap(),
            false, TriggerType.FORCED, cancelMonitor)
          .get();
        
        var issues = analysisResults.rawIssues().stream().map(i -> new RawIssueResponse(
          i.getRuleKey(),
          i.getMessage(),
          i.getSeverity() == null ? null : i.getSeverity().name(),
          i.getFileUri() == null ? null : i.getFileUri().getPath(),
          i.getTextRange()
        )).toList();
        
        return issues.stream();
      } catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }).toList();

    return new AnalysisResult(allIssues);
  }

  public record AnalysisRequest(List<String> fileList) {
  }

  public record AnalysisResult(List<RawIssueResponse> issues) {
  }

  public record RawIssueResponse(String ruleKey, String message, @Nullable String severity, @Nullable String filePath, @Nullable TextRange textRange) {
  }

}
