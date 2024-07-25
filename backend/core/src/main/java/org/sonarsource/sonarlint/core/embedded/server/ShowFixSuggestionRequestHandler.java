/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarCloudConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarQubeConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.ChangesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.FileEditDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.FixSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.LineRangeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.ShowFixSuggestionParams;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Named
@Singleton
public class ShowFixSuggestionRequestHandler implements HttpRequestHandler {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final SonarLintRpcClient client;
  private final TelemetryService telemetryService;
  private final boolean canOpenFixSuggestion;
  private final RequestHandlerBindingAssistant requestHandlerBindingAssistant;
  private final PathTranslationService pathTranslationService;
  private final String sonarCloudUrl;

  public ShowFixSuggestionRequestHandler(SonarLintRpcClient client, TelemetryService telemetryService, InitializeParams params,
    RequestHandlerBindingAssistant requestHandlerBindingAssistant, PathTranslationService pathTranslationService, SonarCloudActiveEnvironment sonarCloudActiveEnvironment) {
    this.client = client;
    this.telemetryService = telemetryService;
    this.canOpenFixSuggestion = params.getFeatureFlags().canOpenFixSuggestion();
    this.requestHandlerBindingAssistant = requestHandlerBindingAssistant;
    this.pathTranslationService = pathTranslationService;
    this.sonarCloudUrl = sonarCloudActiveEnvironment.getUri().toString();
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    var showFixSuggestionQuery = extractQuery(request);
    if (!canOpenFixSuggestion || !Method.POST.isSame(request.getMethod()) || !showFixSuggestionQuery.isValid()) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }
    // TODO: Telemetry

    AssistCreatingConnectionParams serverConnectionParams = createAssistServerConnectionParams(showFixSuggestionQuery);

    requestHandlerBindingAssistant.assistConnectionAndBindingIfNeededAsync(
      serverConnectionParams,
      showFixSuggestionQuery.projectKey,
      (connectionId, configScopeId, cancelMonitor) -> {
        if (configScopeId != null) {
          showFixSuggestionForScope(configScopeId, showFixSuggestionQuery.issueKey, showFixSuggestionQuery.branch, showFixSuggestionQuery.fixSuggestion);
        }
      });

    response.setCode(HttpStatus.SC_OK);
    response.setEntity(new StringEntity("OK"));
  }

  private static AssistCreatingConnectionParams createAssistServerConnectionParams(ShowFixSuggestionQuery query) {
    String tokenName = query.getTokenName();
    String tokenValue = query.getTokenValue();
    return query.isSonarCloud ?
      new AssistCreatingConnectionParams(new SonarCloudConnectionParams(query.getOrganizationKey(), tokenName, tokenValue))
      : new AssistCreatingConnectionParams(new SonarQubeConnectionParams(query.getServerUrl(), tokenName, tokenValue));
  }

  private boolean isSonarCloud(ClassicHttpRequest request) throws ProtocolException {
    return Optional.ofNullable(request.getHeader("Origin"))
      .map(NameValuePair::getValue)
      .map(sonarCloudUrl::equals)
      .orElse(false);
  }

  private void showFixSuggestionForScope(String configScopeId, String issueKey, String branch, FixSuggestionPayload fixSuggestion) {
    pathTranslationService.getOrComputePathTranslation(configScopeId).ifPresent(translation -> {
      var fixSuggestionDto = new FixSuggestionDto(
        fixSuggestion.suggestionId,
        fixSuggestion.getExplanation(),
        new FileEditDto(
          translation.serverToIdePath(Paths.get(fixSuggestion.fileEdit.path)),
          fixSuggestion.fileEdit.changes.stream().map(c ->
            new ChangesDto(
              new LineRangeDto(c.beforeLineRange.startLine, c.beforeLineRange.endLine),
              c.before,
              c.after)
          ).collect(Collectors.toList())
        )
      );
      client.showFixSuggestion(new ShowFixSuggestionParams(configScopeId, issueKey, branch, fixSuggestionDto));
    });
  }

  @VisibleForTesting
  ShowFixSuggestionQuery extractQuery(ClassicHttpRequest request) throws HttpException, IOException {
    var params = new HashMap<String, String>();
    try {
      new URIBuilder(request.getUri(), StandardCharsets.UTF_8)
        .getQueryParams()
        .forEach(p -> params.put(p.getName(), p.getValue()));
    } catch (URISyntaxException e) {
      // Ignored
    }
    var payload = extractAndValidatePayload(request);
    boolean isSonarCloud = isSonarCloud(request);
    String serverUrl = isSonarCloud ? sonarCloudUrl : params.get("server");
    return new ShowFixSuggestionQuery(serverUrl, params.get("project"), params.get("issue"), params.get("branch"),
      params.get("tokenName"), params.get("tokenValue"), params.get("organizationKey"), isSonarCloud, payload);
  }

  private static FixSuggestionPayload extractAndValidatePayload(ClassicHttpRequest request) throws IOException, ParseException {
    var requestEntityString = EntityUtils.toString(request.getEntity(), "UTF-8");
    FixSuggestionPayload payload = null;
    try {
      payload = new Gson().fromJson(requestEntityString, FixSuggestionPayload.class);
    } catch (Exception e) {
      // will be converted to HTTP response later
      LOG.error("Could not deserialize fix suggestion payload", e);
    }
    return payload;
  }

  @VisibleForTesting
  public static class ShowFixSuggestionQuery {
    private final String serverUrl;
    private final String projectKey;
    private final String issueKey;
    private final String branch;
    @Nullable
    private final String tokenName;
    @Nullable
    private final String tokenValue;
    @Nullable
    private final String organizationKey;
    private final boolean isSonarCloud;
    private final FixSuggestionPayload fixSuggestion;

    public ShowFixSuggestionQuery(@Nullable String serverUrl, String projectKey, String issueKey, String branch,
      @Nullable String tokenName, @Nullable String tokenValue, @Nullable String organizationKey, boolean isSonarCloud,
      FixSuggestionPayload fixSuggestion) {
      this.serverUrl = serverUrl;
      this.projectKey = projectKey;
      this.issueKey = issueKey;
      this.branch = branch;
      this.tokenName = tokenName;
      this.tokenValue = tokenValue;
      this.organizationKey = organizationKey;
      this.isSonarCloud = isSonarCloud;
      this.fixSuggestion = fixSuggestion;
    }

    public boolean isValid() {
      return isNotBlank(projectKey) && isNotBlank(issueKey) && isNotBlank(branch)
        && (isSonarCloud || isNotBlank(serverUrl))
        && (!isSonarCloud || isNotBlank(organizationKey))
        && fixSuggestion.isValid() && isTokenValid();
    }

    /**
     * Either we get a token combination or we don't get a token combination: There is nothing in between
     */
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

    public FixSuggestionPayload getFixSuggestion() {
      return fixSuggestion;
    }
  }

  @VisibleForTesting
  public static class FixSuggestionPayload {
    // TODO: Attribute names will be modified to align with SC/SQ
    private final FileEditPayload fileEdit;
    private final String suggestionId;
    private final String explanation;

    public FixSuggestionPayload(FileEditPayload fileEdit, String suggestionId, String explanation) {
      this.fileEdit = fileEdit;
      this.suggestionId = suggestionId;
      this.explanation = explanation;
    }

    public boolean isValid() {
      return fileEdit.isValid() && !suggestionId.isBlank();
    }

    public FileEditPayload getFileEdit() {
      return fileEdit;
    }

    public String getSuggestionId() {
      return suggestionId;
    }

    public String getExplanation() {
      return explanation;
    }
  }

  @VisibleForTesting
  public static class FileEditPayload {
    private final List<ChangesPayload> changes;
    private final String path;

    public FileEditPayload(List<ChangesPayload> changes, String path) {
      this.changes = changes;
      this.path = path;
    }

    public boolean isValid() {
      return !path.isBlank() && changes.stream().allMatch(ChangesPayload::isValid);
    }

    public List<ChangesPayload> getChanges() {
      return changes;
    }

    public String getPath() {
      return path;
    }
  }

  @VisibleForTesting
  public static class ChangesPayload {
    private final TextRangePayload beforeLineRange;
    private final String before;
    private final String after;

    public ChangesPayload(TextRangePayload beforeLineRange, String before, String after) {
      this.beforeLineRange = beforeLineRange;
      this.before = before;
      this.after = after;
    }

    public boolean isValid() {
      return beforeLineRange.isValid();
    }

    public TextRangePayload getBeforeLineRange() {
      return beforeLineRange;
    }

    public String getBefore() {
      return before;
    }

    public String getAfter() {
      return after;
    }
  }

  @VisibleForTesting
  public static class TextRangePayload {
    private final int startLine;
    private final int endLine;

    public TextRangePayload(int startLine, int endLine) {
      this.startLine = startLine;
      this.endLine = endLine;
    }

    public boolean isValid() {
      return startLine >= 0 && endLine >= 0 && startLine <= endLine;
    }

    public int getStartLine() {
      return startLine;
    }

    public int getEndLine() {
      return endLine;
    }
  }

}
