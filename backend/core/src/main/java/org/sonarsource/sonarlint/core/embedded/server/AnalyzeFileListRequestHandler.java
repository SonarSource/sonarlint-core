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
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.sonarsource.sonarlint.core.tracking.TaintVulnerabilityTrackingService;

public class AnalyzeFileListRequestHandler implements HttpRequestHandler {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final AnalysisService analysisService;
  private final ClientFileSystemService clientFileSystemService;
  private final TaintVulnerabilityTrackingService taintService;
  private final Gson gson = new Gson();

  public AnalyzeFileListRequestHandler(AnalysisService analysisService, ClientFileSystemService clientFileSystemService,
    TaintVulnerabilityTrackingService taintService) {
    this.analysisService = analysisService;
    this.clientFileSystemService = clientFileSystemService;
    this.taintService = taintService;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext httpContext) throws HttpException, IOException {
    LOG.debug("Received request for analyzing a list of files");

    if (!Method.POST.isSame(request.getMethod())) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }

    AnalyzeFileListRequest analysisRequest;
    try {
      var requestBody = EntityUtils.toString(request.getEntity(), "UTF-8");
      analysisRequest = gson.fromJson(requestBody, AnalyzeFileListRequest.class);
    } catch (Exception e) {
      LOG.warn("Failed to parse analyze file list request", e);
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      response.setEntity(new StringEntity("Failed to parse analyze file list request", ContentType.APPLICATION_JSON));
      return;
    }

    if (analysisRequest == null || analysisRequest.fileAbsolutePaths == null || analysisRequest.fileAbsolutePaths.isEmpty()) {
      LOG.warn("Empty or invalid file list in analyze request");
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      response.setEntity(new StringEntity("Empty or invalid file list in analyze request", ContentType.APPLICATION_JSON));
      return;
    }

    try {
      response.setEntity(new StringEntity(new Gson().toJson(analyze(analysisRequest)), ContentType.APPLICATION_JSON));
    } catch (Exception e) {
      LOG.error("Failed to analyze files", e);
      response.setCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
      response.setEntity(new StringEntity("Failed to analyze files, reason: " + e.getMessage(), ContentType.APPLICATION_JSON));
    }
  }

  private AnalyzeFileListResult analyze(AnalyzeFileListRequest request) {
    var cancelMonitor = new SonarLintCancelMonitor();
    var filePaths = request.fileAbsolutePaths.stream()
      .map(path -> Paths.get(path).toUri().normalize())
      .collect(Collectors.toSet());

    LOG.debug("Analyzing {} files: {}", filePaths.size(), filePaths);

    var filesByScope = clientFileSystemService.groupFilesByConfigScope(filePaths);

    if (filesByScope.isEmpty()) {
      LOG.warn("No files belong to any configured scope, skipping analysis");
      throw new IllegalStateException("No files were found to be indexed by SonarQube for IDE");
    }

    var allIssues = filesByScope.entrySet().stream().flatMap(entry -> {
      var configScopeId = entry.getKey();
      var files = entry.getValue();
      LOG.info("Analyzing {} files: {}", files.size(), files);
      try {
        var taints = getTaintsAsRawFindings(configScopeId, files, cancelMonitor);
        var issues = getIssuesAndHotspotsAsRawFindings(configScopeId, files, cancelMonitor);
        return Stream.concat(taints, issues);
      } catch (ExecutionException | InterruptedException e) {
        LOG.error("Failed to analyze files for config scope {}", configScopeId, e);
        throw new RuntimeException(e);
      }
    }).toList();

    return new AnalyzeFileListResult(allIssues);
  }

  private Stream<RawFindingResponse> getTaintsAsRawFindings(String configScopeId, Set<URI> files, SonarLintCancelMonitor cancelMonitor) {
    return taintService.listAll(configScopeId, true, cancelMonitor)
      .stream()
      .filter(taint -> files.contains(taint.getIdeFilePath().toUri()))
      .map(taint -> {
        var isMqrMode = taint.getSeverityMode().isRight();
        var textRange = taint.getTextRange();
        return new RawFindingResponse(
          taint.getRuleKey(),
          taint.getMessage(),
          isMqrMode ? taint.getSeverityMode().getRight().getImpacts().stream()
            .map(impact -> impact.getImpactSeverity().name())
            .collect(Collectors.joining(",")) : taint.getSeverityMode().getLeft().getSeverity().name(),
          taint.getIdeFilePath().toString(),
          textRange == null ? null : new TextRange(textRange.getStartLine(), textRange.getStartLineOffset(), textRange.getEndLine(), textRange.getEndLineOffset())
        );
      });
  }

  private Stream<RawFindingResponse> getIssuesAndHotspotsAsRawFindings(String configScopeId, Set<URI> files, SonarLintCancelMonitor cancelMonitor)
    throws ExecutionException, InterruptedException {
    return analysisService.scheduleAnalysis(configScopeId, UUID.randomUUID(), files, Collections.emptyMap(),
        false, TriggerType.FORCED, cancelMonitor)
      .thenApplyAsync(results ->
        results.rawIssues().stream().map(i -> new RawFindingResponse(
          i.getRuleKey(),
          i.getMessage(),
          i.getSeverity() == null ? null : i.getSeverity().name(),
          i.getFileUri() == null ? null : i.getFileUri().getPath(),
          i.getTextRange()
        )))
      .get();
  }

  public record AnalyzeFileListRequest(List<String> fileAbsolutePaths) {
  }

  public record AnalyzeFileListResult(List<RawFindingResponse> findings) {
  }

  public record RawFindingResponse(String ruleKey, String message, @Nullable String severity, @Nullable String filePath, @Nullable TextRange textRange) {
  }

}
