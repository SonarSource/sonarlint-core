/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.embedded.server.handler;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.Strings;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.embedded.server.AttributeUtils;
import org.sonarsource.sonarlint.core.embedded.server.RequestHandlerBindingAssistant;
import org.sonarsource.sonarlint.core.event.FixSuggestionReceivedEvent;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarCloudConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarQubeConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.BatchFixSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.ChangesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.LineRangeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.ShowBatchFixSuggestionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.SingleEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiSuggestionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.springframework.context.ApplicationEventPublisher;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.sonarsource.sonarlint.core.commons.util.StringUtils.sanitizeAgainstRTLO;

public class ShowBatchFixSuggestionRequestHandler implements HttpRequestHandler {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintRpcClient client;
  private final ApplicationEventPublisher eventPublisher;
  private final RequestHandlerBindingAssistant requestHandlerBindingAssistant;
  private final PathTranslationService pathTranslationService;
  private final SonarCloudActiveEnvironment sonarCloudActiveEnvironment;
  private final ClientFileSystemService clientFs;

  public ShowBatchFixSuggestionRequestHandler(SonarLintRpcClient client, ApplicationEventPublisher eventPublisher,
    RequestHandlerBindingAssistant requestHandlerBindingAssistant,
    PathTranslationService pathTranslationService, SonarCloudActiveEnvironment sonarCloudActiveEnvironment, ClientFileSystemService clientFs) {
    this.client = client;
    this.eventPublisher = eventPublisher;
    this.requestHandlerBindingAssistant = requestHandlerBindingAssistant;
    this.pathTranslationService = pathTranslationService;
    this.sonarCloudActiveEnvironment = sonarCloudActiveEnvironment;
    this.clientFs = clientFs;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    var origin = AttributeUtils.getOrigin(context);
    var showBatchFixSuggestionQuery = extractQuery(request, origin, AttributeUtils.getParams(context));

    if (!Method.POST.isSame(request.getMethod()) || !showBatchFixSuggestionQuery.isValid()) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }

    var totalChangesCount = showBatchFixSuggestionQuery.getBatchFixSuggestion().edits.size();
    var suggestionId = UUID.randomUUID().toString();
    eventPublisher.publishEvent(new FixSuggestionReceivedEvent(suggestionId,
      showBatchFixSuggestionQuery.isSonarCloud ? AiSuggestionSource.SONARCLOUD : AiSuggestionSource.SONARQUBE,
      totalChangesCount, true));

    AssistCreatingConnectionParams serverConnectionParams = createAssistServerConnectionParams(showBatchFixSuggestionQuery, sonarCloudActiveEnvironment);

    requestHandlerBindingAssistant.assistConnectionAndBindingIfNeededAsync(
      serverConnectionParams,
      showBatchFixSuggestionQuery.projectKey, origin,
      (connectionId, boundScopes, configScopeId, cancelMonitor) -> {
        if (configScopeId != null) {
          if (doAllClientFilesExist(configScopeId, showBatchFixSuggestionQuery.batchFixSuggestion.edits, boundScopes)) {
            showBatchFixSuggestionForScope(configScopeId, showBatchFixSuggestionQuery.issueKey, showBatchFixSuggestionQuery.batchFixSuggestion);
          } else {
            client.showMessage(new ShowMessageParams(MessageType.ERROR, "Attempted to show a batch fix suggestion for files that are " +
              "not known by SonarQube for IDE"));
          }
        }
      });

    response.setCode(HttpStatus.SC_OK);
    response.setEntity(new StringEntity("OK"));
  }

  private boolean doAllClientFilesExist(String configScopeId, List<SingleEditPayload> edits, Collection<String> boundScopes) {
    var optTranslation = pathTranslationService.getOrComputePathTranslation(configScopeId);
    if (optTranslation.isPresent()) {
      var translation = optTranslation.get();
      for (var edit : edits) {
        var idePath = translation.serverToIdePath(Paths.get(edit.path));
        var fileFound = false;
        for (var scope : boundScopes) {
          for (var file : clientFs.getFiles(scope)) {
            if (Path.of(file.getUri()).endsWith(idePath)) {
              fileFound = true;
              break;
            }
          }
          if (fileFound) {
            break;
          }
        }
        if (!fileFound) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static AssistCreatingConnectionParams createAssistServerConnectionParams(ShowBatchFixSuggestionQuery query, SonarCloudActiveEnvironment sonarCloudActiveEnvironment) {
    String tokenName = query.getTokenName();
    String tokenValue = query.getTokenValue();
    if (query.isSonarCloud) {
      var region = sonarCloudActiveEnvironment.getRegionOrThrow(query.getServerUrl());
      return new AssistCreatingConnectionParams(new SonarCloudConnectionParams(query.getOrganizationKey(), tokenName, tokenValue, SonarCloudRegion.valueOf(region.name())));
    } else {
      return new AssistCreatingConnectionParams(new SonarQubeConnectionParams(query.getServerUrl(), tokenName, tokenValue));
    }
  }

  private void showBatchFixSuggestionForScope(String configScopeId, String issueKey, BatchFixSuggestionPayload batchFixSuggestion) {
    pathTranslationService.getOrComputePathTranslation(configScopeId).ifPresent(translation -> {
      var singleEditDtos = batchFixSuggestion.edits.stream()
        .map(edit -> new SingleEditDto(
          translation.serverToIdePath(Paths.get(edit.path)),
          new ChangesDto(
            new LineRangeDto(edit.beforeLineRange.startLine, edit.beforeLineRange.endLine),
            edit.before,
            edit.after)
        ))
        .toList();

      var batchFixSuggestionDto = new BatchFixSuggestionDto(singleEditDtos);
      client.showBatchFixSuggestion(new ShowBatchFixSuggestionParams(configScopeId, issueKey, batchFixSuggestionDto));
    });
  }

  @VisibleForTesting
  ShowBatchFixSuggestionQuery extractQuery(ClassicHttpRequest request, String origin, Map<String, String> params) throws HttpException, IOException {
    var payload = extractAndValidatePayload(request);
    boolean isSonarCloud = sonarCloudActiveEnvironment.isSonarQubeCloud(origin);
    String serverUrl;
    if (isSonarCloud) {
      serverUrl = Strings.CS.removeEnd(origin, "/");
    } else {
      serverUrl = params.get("server");
    }
    return new ShowBatchFixSuggestionQuery(serverUrl, params.get("project"), params.get("issue"), params.get("branch"),
      params.get("tokenName"), params.get("tokenValue"), params.get("organizationKey"), isSonarCloud, payload);
  }

  private static BatchFixSuggestionPayload extractAndValidatePayload(ClassicHttpRequest request) throws IOException, ParseException {
    var requestEntityString = EntityUtils.toString(request.getEntity(), "UTF-8");
    BatchFixSuggestionPayload payload = null;
    try {
      payload = new Gson().fromJson(requestEntityString, BatchFixSuggestionPayload.class);
    } catch (Exception e) {
      LOG.error("Could not deserialize batch fix suggestion payload", e);
    }
    return payload;
  }

  @VisibleForTesting
  public static class ShowBatchFixSuggestionQuery {
    private final String serverUrl;
    private final String projectKey;
    private final String issueKey;
    @Nullable
    private final String branch;
    @Nullable
    private final String tokenName;
    @Nullable
    private final String tokenValue;
    @Nullable
    private final String organizationKey;
    private final boolean isSonarCloud;
    private final BatchFixSuggestionPayload batchFixSuggestion;

    public ShowBatchFixSuggestionQuery(@Nullable String serverUrl, String projectKey, String issueKey, @Nullable String branch,
      @Nullable String tokenName, @Nullable String tokenValue, @Nullable String organizationKey, boolean isSonarCloud,
      BatchFixSuggestionPayload batchFixSuggestion) {
      this.serverUrl = serverUrl;
      this.projectKey = projectKey;
      this.issueKey = issueKey;
      this.branch = branch;
      this.tokenName = tokenName;
      this.tokenValue = tokenValue;
      this.organizationKey = organizationKey;
      this.isSonarCloud = isSonarCloud;
      this.batchFixSuggestion = batchFixSuggestion;
    }

    public boolean isValid() {
      return isNotBlank(projectKey) && isNotBlank(issueKey)
        && (isSonarCloud || isNotBlank(serverUrl))
        && (!isSonarCloud || isNotBlank(organizationKey))
        && batchFixSuggestion != null && batchFixSuggestion.isValid() && isTokenValid();
    }

    public boolean isTokenValid() {
      if (tokenName != null && tokenValue != null) {
        return isNotEmpty(tokenName) && isNotEmpty(tokenValue);
      }

      return tokenName == null && tokenValue == null;
    }

    public String getServerUrl() {
      return serverUrl;
    }

    public String getProjectKey() {
      return projectKey;
    }

    @Nullable
    public String getOrganizationKey() {
      return organizationKey;
    }

    public String getIssueKey() {
      return issueKey;
    }

    @Nullable
    public String getBranch() {
      return branch;
    }

    @Nullable
    public String getTokenName() {
      return tokenName;
    }

    @Nullable
    public String getTokenValue() {
      return tokenValue;
    }

    public BatchFixSuggestionPayload getBatchFixSuggestion() {
      return batchFixSuggestion;
    }
  }

  @VisibleForTesting
  public record BatchFixSuggestionPayload(List<SingleEditPayload> edits) {

    public boolean isValid() {
      return edits != null && !edits.isEmpty() && edits.stream().allMatch(SingleEditPayload::isValid);
    }

  }

  @VisibleForTesting
  public record SingleEditPayload(String path, TextRangePayload beforeLineRange, String before, String after) {

    public SingleEditPayload(String path, TextRangePayload beforeLineRange, String before, String after) {
      this.path = path;
      this.beforeLineRange = beforeLineRange;
      this.before = sanitizeAgainstRTLO(before);
      this.after = sanitizeAgainstRTLO(after);
    }

    public boolean isValid() {
      return path != null && !path.isBlank() && beforeLineRange != null && beforeLineRange.isValid();
    }

  }

  @VisibleForTesting
  public record TextRangePayload(int startLine, int endLine) {

    public boolean isValid() {
      return startLine >= 0 && endLine >= 0 && startLine <= endLine;
    }

  }

}
