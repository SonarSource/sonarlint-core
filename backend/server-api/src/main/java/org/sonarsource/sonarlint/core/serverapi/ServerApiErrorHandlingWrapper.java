/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.commons.Transition;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.branches.ServerBranch;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverapi.developers.Event;
import org.sonarsource.sonarlint.core.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarlint.core.serverapi.exception.UnauthorizedException;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspotDetails;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.organization.ServerOrganization;
import org.sonarsource.sonarlint.core.serverapi.plugins.ServerPlugin;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.push.SonarServerEvent;
import org.sonarsource.sonarlint.core.serverapi.qualityprofile.QualityProfile;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerRule;
import org.sonarsource.sonarlint.core.serverapi.stream.EventStream;
import org.sonarsource.sonarlint.core.serverapi.system.ServerStatusInfo;
import org.sonarsource.sonarlint.core.serverapi.system.ValidationResult;

public class ServerApiErrorHandlingWrapper {

  private final ServerApi serverApi;
  private final Runnable notifyClient;

  public ServerApiErrorHandlingWrapper(ServerApi serverApi, Runnable notifyClient) {
    this.serverApi = serverApi;
    this.notifyClient = notifyClient;
  }

  public IssueApi.IssuesPullResult pullIssues(String projectKey, String branchName, Set<SonarLanguage> enabledLanguages, @Nullable Long changedSince,
    SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.issue().pullIssues(projectKey, branchName, enabledLanguages, changedSince, cancelMonitor));
  }

  public boolean isSonarCloud() {
    return serverApi.isSonarCloud();
  }

  public List<ScannerInput.ServerIssue> downloadAllFromBatchIssues(String key, @Nullable String branchName, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.issue().downloadAllFromBatchIssues(key, branchName, cancelMonitor));
  }

  public Set<String> getAllTaintRules(SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.rules().getAllTaintRules(List.of(SonarLanguage.values()), cancelMonitor));
  }

  public IssueApi.DownloadIssuesResult downloadVulnerabilitiesForRules(String key, Set<String> taintRuleKeys, @Nullable String branchName, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.issue().downloadVulnerabilitiesForRules(key, taintRuleKeys, branchName, cancelMonitor));
  }

  public String getRawSourceCode(String fileKey, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.source().getRawSourceCode(fileKey, cancelMonitor).orElse(""));
  }

  public HotspotApi.HotspotsPullResult pullHotspots(String projectKey, String branchName, Set<SonarLanguage> enabledLanguages, @Nullable Long changedSince,
    SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.hotspot().pullHotspots(projectKey, branchName, enabledLanguages, changedSince, cancelMonitor));
  }

  public ServerHotspotDetails showHotspot(String hotspotKey, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.hotspot().show(hotspotKey, cancelMonitor));
  }

  public Collection<ServerHotspot> getAllServerHotspots(String projectKey, String branchName, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.hotspot().getAll(projectKey, branchName, cancelMonitor));
  }

  public void anticipatedTransitions(String sonarProjectKey, List<LocalOnlyIssue> localOnlyIssues, SonarLintCancelMonitor cancelMonitor) {
    callWithErrorHandleAndRethrow(() -> serverApi.issue().anticipatedTransitions(sonarProjectKey, localOnlyIssues, cancelMonitor));
  }

  public void changeIssueStatus(String issueKey, Transition reviewStatus, SonarLintCancelMonitor cancelMonitor) {
    callWithErrorHandleAndRethrow(() -> serverApi.issue().changeStatus(issueKey, reviewStatus, cancelMonitor));
  }

  public void changeHotspotStatus(String hotspotKey, HotspotReviewStatus newStatus, SonarLintCancelMonitor cancelMonitor) {
    callWithErrorHandleAndRethrow(() -> serverApi.hotspot().changeStatus(hotspotKey, newStatus, cancelMonitor));
  }

  public boolean supportHotspotsPull(Version version) {
    return callWithErrorHandleAndRethrow(() -> serverApi.hotspot().supportHotspotsPull(version));
  }

  public Collection<ServerHotspot> getHotspotsForFile(String projectKey, Path serverFilePath, String branchName, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.hotspot().getFromFile(projectKey, serverFilePath, branchName, cancelMonitor));
  }

  public Optional<ServerHotspotDetails> tryFetchHotspot(String hotspotKey, SonarLintCancelMonitor cancelMonitor) {
    return serverApi.hotspot().fetch(hotspotKey, cancelMonitor);
  }

  public ServerStatusInfo getSystemStatus(SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.system().getStatus(cancelMonitor));
  }

  public Optional<ServerRule> tryFetchRule(String ruleKey, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.rules().getRule(ruleKey, cancelMonitor));
  }

  public Optional<IssueApi.ServerIssueDetails> tryFetchIssue(String issueKey, String projectKey, String branch, @Nullable String pullRequest,
    SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.issue().fetchServerIssue(issueKey, projectKey, branch, pullRequest, cancelMonitor));
  }

  public Optional<String> tryFetchCodeSnippet(String fileKey, Common.TextRange textRange, String branch, @Nullable String pullRequest,
    SonarLintCancelMonitor cancelMonitor) {
    return serverApi.issue().getCodeSnippet(fileKey, textRange, branch, pullRequest, cancelMonitor);
  }

  public Map<String, String> getSettings(String projectKey, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.settings().getProjectSettings(projectKey, cancelMonitor));
  }

  public List<QualityProfile> getQualityProfiles(String projectKey, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.qualityProfile().getQualityProfiles(projectKey, cancelMonitor));
  }

  public Collection<ServerActiveRule> getAllActiveRules(String profileKey, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.rules().getAllActiveRules(profileKey, cancelMonitor));
  }

  public List<ServerPlugin> getInstalledPlugins(SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.plugins().getInstalled(cancelMonitor));
  }

  public void getPlugin(String pluginKey, ServerApiHelper.IOConsumer<InputStream> pluginFileConsumer, SonarLintCancelMonitor cancelMonitor) {
    callWithErrorHandleAndRethrow(() -> serverApi.plugins().getPlugin(pluginKey, pluginFileConsumer, cancelMonitor));
  }

  public Optional<NewCodeDefinition> getNewCodeDefinition(String projectKey, Version version, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.newCodeApi().getNewCodeDefinition(projectKey, null, version, cancelMonitor));
  }

  public String getGlobalSetting(String mqrModeSetting, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.settings().getGlobalSetting(mqrModeSetting, cancelMonitor));
  }

  public IssueApi.TaintIssuesPullResult pullTaints(String projectKey, String branchName, Set<SonarLanguage> enabledLanguages, @Nullable Long changedSince,
    SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.issue().pullTaintIssues(projectKey, branchName, enabledLanguages, changedSince, cancelMonitor));
  }

  public List<ServerOrganization> listUserOrganizations(SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.organization().listUserOrganizations(cancelMonitor));
  }

  public Optional<ServerOrganization> getOrganization(String organizationKey, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.organization().getOrganization(organizationKey, cancelMonitor));
  }

  public boolean isSmartNotificationsSupported(SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.developers().isSupported(cancelMonitor));
  }

  public List<Event> getDevEvents(Map<String, ZonedDateTime> projectKeysByLastEventPolling, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.developers().getEvents(projectKeysByLastEventPolling, cancelMonitor));
  }

  public EventStream attemptSubscription(Set<String> projectKeys, Set<SonarLanguage> enabledLanguages, Consumer<SonarServerEvent> eventConsumer) {
    return callWithErrorHandleAndRethrow(() -> serverApi.push().subscribe(projectKeys, enabledLanguages, e -> notifyHandlers(e, eventConsumer)));
  }

  private static void notifyHandlers(SonarServerEvent sonarServerEvent, Consumer<SonarServerEvent> clientEventConsumer) {
    clientEventConsumer.accept(sonarServerEvent);
  }

  public void addIssueComment(String issueKey, String comment, SonarLintCancelMonitor cancelMonitor) {
    callWithErrorHandleAndRethrow(() -> serverApi.issue().addComment(issueKey, comment, cancelMonitor));
  }

  public ValidationResult validateAuthentication(SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.authentication().validate(cancelMonitor));
  }

  public List<ServerProject> getAllProjects(SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.component().getAllProjects(cancelMonitor));
  }

  public Optional<ServerProject> getProject(String projectKey, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.component().getProject(projectKey, cancelMonitor));
  }

  public Issues.Issue searchIssueByKey(String issueKey, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.issue().searchByKey(issueKey, cancelMonitor));
  }

  public void revokeToken(String tokenName, SonarLintCancelMonitor cancelMonitor) {
    callWithErrorHandleAndRethrow(() -> serverApi.userTokens().revoke(tokenName, cancelMonitor));
  }

  public List<String> getAllFileKeys(String projectKey, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.component().getAllFileKeys(projectKey, cancelMonitor));
  }

  public List<ServerBranch> getProjectBranches(String projectKey, SonarLintCancelMonitor cancelMonitor) {
    return callWithErrorHandleAndRethrow(() -> serverApi.branches().getAllBranches(projectKey, cancelMonitor));
  }

  private <T> T callWithErrorHandleAndRethrow(Supplier<T> apiCall) {
    try {
      return apiCall.get();
    } catch (UnauthorizedException | ForbiddenException e) {
      // here it's possible to call different notifications depending on the exception
      notifyClient.run();
      throw e;
    }
  }

  private void callWithErrorHandleAndRethrow(Runnable apiCall) {
    try {
      apiCall.run();
    } catch (UnauthorizedException | ForbiddenException e) {
      notifyClient.run();
      throw e;
    }
  }

}
