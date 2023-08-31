/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonarsource.sonarlint.core.analysis.AnalysisEngine;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MapSettings;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.DefaultClientIssue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBranches;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoadResult;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsLoader.Configuration;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfiguration;
import org.sonarsource.sonarlint.core.serverconnection.IssueStorePaths;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.ServerConnection;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public final class ConnectedSonarLintEngineImpl extends AbstractSonarLintEngine implements ConnectedSonarLintEngine {

  private final ConnectedGlobalConfiguration globalConfig;
  private final ServerConnection serverConnection;
  private final AtomicReference<AnalysisContext> analysisContext = new AtomicReference<>();

  public ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration globalConfig) {
    super(globalConfig.getLogOutput());
    this.globalConfig = globalConfig;

    serverConnection = new ServerConnection(globalConfig.getStorageRoot(), globalConfig.getConnectionId(), globalConfig.isSonarCloud(), globalConfig.getEnabledLanguages(),
      globalConfig.getEmbeddedPluginPathsByKey().keySet(), globalConfig.getWorkDir());
    start();
  }

  @Override
  public AnalysisEngine getAnalysisEngine() {
    return analysisContext.get().analysisEngine;
  }

  public AnalysisContext start() {
    setLogging(null);
    return analysisContext.getAndSet(loadAnalysisContext());
  }

  private AnalysisContext loadAnalysisContext() {
    var loadingResult = loadPlugins();
    var pluginDetails = loadingResult.getPluginCheckResultByKeys().values().stream().map(p -> new PluginDetails(p.getPlugin().getKey(), p.getPlugin().getName(),
      Optional.ofNullable(p.getPlugin().getVersion()).map(Version::toString).orElse(null), p.getSkipReason().orElse(null))).collect(Collectors.toList());

    var allRulesDefinitionsByKey = loadPluginMetadata(loadingResult.getLoadedPlugins(), globalConfig.getEnabledLanguages(), true, globalConfig.isHotspotsEnabled());

    var analysisGlobalConfig = AnalysisEngineConfiguration.builder()
      .setClientPid(globalConfig.getClientPid())
      .setExtraProperties(globalConfig.extraProperties())
      .setNodeJs(globalConfig.getNodeJsPath())
      .setWorkDir(globalConfig.getWorkDir())
      .setModulesProvider(globalConfig.getModulesProvider())
      .build();
    var analysisEngine = new AnalysisEngine(analysisGlobalConfig, loadingResult.getLoadedPlugins(), logOutput);
    return new AnalysisContext(pluginDetails, allRulesDefinitionsByKey, analysisEngine);
  }

  private PluginsLoadResult loadPlugins() {
    Map<String, Path> pluginsToLoadByKey = new HashMap<>();
    // order is important as e.g. embedded takes precedence over stored
    pluginsToLoadByKey.putAll(serverConnection.getStoredPluginPathsByKey());
    pluginsToLoadByKey.putAll(globalConfig.getEmbeddedPluginPathsByKey());
    Set<Path> plugins = new HashSet<>(pluginsToLoadByKey.values());

    var config = new Configuration(plugins, globalConfig.getEnabledLanguages(), Optional.ofNullable(globalConfig.getNodeJsVersion()));
    return new PluginsLoader().load(config);
  }

  private static class ActiveRulesContext {
    private final boolean shouldSkipCleanCodeTaxonomy;
    private final List<ActiveRule> activeRules = new ArrayList<>();
    private final Map<String, ActiveRuleMetadata> activeRulesMetadata = new HashMap<>();

    private ActiveRulesContext(boolean shouldSkipCleanCodeTaxonomy) {
      this.shouldSkipCleanCodeTaxonomy = shouldSkipCleanCodeTaxonomy;
    }

    public void includeRule(SonarLintRuleDefinition ruleOrTemplateDefinition, ServerActiveRule activeRule) {
      var activeRuleForAnalysis = new ActiveRule(activeRule.getRuleKey(), ruleOrTemplateDefinition.getLanguage().getLanguageKey());
      activeRuleForAnalysis.setTemplateRuleKey(trimToNull(activeRule.getTemplateKey()));
      activeRuleForAnalysis.setParams(getEffectiveParams(ruleOrTemplateDefinition, activeRule));
      activeRules.add(activeRuleForAnalysis);
      activeRulesMetadata.put(activeRule.getRuleKey(),
        new ActiveRuleMetadata(activeRule.getSeverity(), ruleOrTemplateDefinition.getType(),
          ruleOrTemplateDefinition.getCleanCodeAttribute().orElse(CleanCodeAttribute.defaultCleanCodeAttribute()), ruleOrTemplateDefinition.getDefaultImpacts()));
    }

    private static Map<String, String> getEffectiveParams(SonarLintRuleDefinition ruleOrTemplateDefinition, ServerActiveRule activeRule) {
      Map<String, String> effectiveParams = new HashMap<>(ruleOrTemplateDefinition.getDefaultParams());
      activeRule.getParams().forEach((paramName, paramValue) -> {
        if (!ruleOrTemplateDefinition.getParams().containsKey(paramName)) {
          LOG.debug("Rule parameter '{}' for rule '{}' does not exist in embedded analyzer, ignoring.", paramName, ruleOrTemplateDefinition.getKey());
          return;
        }
        effectiveParams.put(paramName, paramValue);
      });
      return effectiveParams;
    }

    public void includeRule(SonarLintRuleDefinition rule) {
      var activeRuleForAnalysis = new ActiveRule(rule.getKey(), rule.getLanguage().getLanguageKey());
      activeRuleForAnalysis.setParams(rule.getDefaultParams());
      activeRules.add(activeRuleForAnalysis);
      activeRulesMetadata.put(activeRuleForAnalysis.getRuleKey(), new ActiveRuleMetadata(rule.getDefaultSeverity(), rule.getType(),
        rule.getCleanCodeAttribute().orElse(CleanCodeAttribute.defaultCleanCodeAttribute()), rule.getDefaultImpacts()));
    }

    private ActiveRuleMetadata getRuleMetadata(String ruleKey) {
      return activeRulesMetadata.get(ruleKey);
    }

    private static class ActiveRuleMetadata {
      private final IssueSeverity severity;
      private final RuleType type;

      private final CleanCodeAttribute cleanCodeAttribute;

      private final Map<SoftwareQuality, ImpactSeverity> defaultImpacts;

      private ActiveRuleMetadata(IssueSeverity severity, RuleType type, CleanCodeAttribute cleanCodeAttribute, Map<SoftwareQuality, ImpactSeverity> defaultImpacts) {
        this.severity = severity;
        this.type = type;
        this.cleanCodeAttribute = cleanCodeAttribute;
        this.defaultImpacts = defaultImpacts;
      }
    }
  }

  @Override
  public AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener, @Nullable ClientLogOutput logOutput,
    @Nullable ClientProgressMonitor monitor) {
    requireNonNull(configuration);
    requireNonNull(issueListener);

    setLogging(logOutput);

    var analysisConfigBuilder = AnalysisConfiguration.builder()
      .addInputFiles(configuration.inputFiles());
    var projectKey = configuration.getProjectKey();
    analysisConfigBuilder.putAllExtraProperties(serverConnection.getAnalyzerConfiguration(projectKey).getSettings().getAll());
    analysisConfigBuilder.putAllExtraProperties(globalConfig.extraProperties());
    var activeRulesContext = buildActiveRulesContext(configuration);
    if (activeRulesContext.activeRules.isEmpty()) {
      LOG.info("Skipping analysis, no synchronization has been made with the server");
      return new AnalysisResults();
    }
    analysisConfigBuilder.putAllExtraProperties(configuration.extraProperties())
      .addActiveRules(activeRulesContext.activeRules)
      .setBaseDir(configuration.baseDir())
      .build();

    var analysisConfiguration = analysisConfigBuilder.build();

    var analyzeCommand = new AnalyzeCommand(configuration.moduleKey(), analysisConfiguration, issue -> streamIssue(issueListener, issue, activeRulesContext), logOutput);
    return postAnalysisCommandAndGetResult(analyzeCommand, monitor);
  }

  private void streamIssue(IssueListener issueListener, Issue newIssue, ActiveRulesContext activeRulesContext) {
    var ruleMetadata = activeRulesContext.getRuleMetadata(newIssue.getRuleKey());
    var vulnerabilityProbability = analysisContext.get().findRule(newIssue.getRuleKey()).flatMap(SonarLintRuleDefinition::getVulnerabilityProbability);
    var effectiveCleanCodeAttribute = activeRulesContext.shouldSkipCleanCodeTaxonomy ? null : ruleMetadata.cleanCodeAttribute;
    var effectiveImpacts = activeRulesContext.shouldSkipCleanCodeTaxonomy ? Map.<SoftwareQuality, ImpactSeverity>of() : ruleMetadata.defaultImpacts;
    issueListener.handle(new DefaultClientIssue(newIssue, ruleMetadata.severity, ruleMetadata.type, effectiveCleanCodeAttribute,
      effectiveImpacts, vulnerabilityProbability));
  }

  private ActiveRulesContext buildActiveRulesContext(ConnectedAnalysisConfiguration configuration) {
    var analysisRulesContext = new ActiveRulesContext(serverConnection.shouldSkipCleanCodeTaxonomy());
    var projectKey = configuration.getProjectKey();
    var ruleSetByLanguageKey = serverConnection.getAnalyzerConfiguration(projectKey).getRuleSetByLanguageKey();
    if (ruleSetByLanguageKey.isEmpty()) {
      // could be the case before the first sync
      return analysisRulesContext;
    }
    ruleSetByLanguageKey.entrySet()
      .stream().filter(e -> Language.forKey(e.getKey()).filter(l -> globalConfig.getEnabledLanguages().contains(l)).isPresent())
      .forEach(e -> {
        var languageKey = e.getKey();
        var ruleSet = e.getValue();

        LOG.debug("  * {}: {} active rules", languageKey, ruleSet.getRules().size());
        for (ServerActiveRule possiblyDeprecatedActiveRuleFromStorage : ruleSet.getRules()) {
          var activeRuleFromStorage = tryConvertDeprecatedKeys(possiblyDeprecatedActiveRuleFromStorage);
          SonarLintRuleDefinition ruleOrTemplateDefinition;
          if (StringUtils.isNotBlank(activeRuleFromStorage.getTemplateKey())) {
            ruleOrTemplateDefinition = analysisContext.get().findRule(activeRuleFromStorage.getTemplateKey()).orElse(null);
            if (ruleOrTemplateDefinition == null) {
              LOG.debug("Rule {} is enabled on the server, but its template {} is not available in SonarLint", activeRuleFromStorage.getRuleKey(),
                activeRuleFromStorage.getTemplateKey());
              continue;
            }
          } else {
            ruleOrTemplateDefinition = analysisContext.get().findRule(activeRuleFromStorage.getRuleKey()).orElse(null);
            if (ruleOrTemplateDefinition == null) {
              LOG.debug("Rule {} is enabled on the server, but not available in SonarLint", activeRuleFromStorage.getRuleKey());
              continue;
            }
          }
          if (shouldIncludeRuleForAnalysis(ruleOrTemplateDefinition)) {
            analysisRulesContext.includeRule(ruleOrTemplateDefinition, activeRuleFromStorage);
          }
        }
      });

    var supportSecretAnalysis = serverConnection.supportsSecretAnalysis();
    if (!supportSecretAnalysis) {
      analysisContext.get().allRulesDefinitionsByKey.values().stream()
        .filter(ruleDefinition -> ruleDefinition.getLanguage() == Language.SECRETS)
        .filter(this::shouldIncludeRuleForAnalysis)
        .forEach(analysisRulesContext::includeRule);
    }
    return analysisRulesContext;
  }

  private boolean shouldIncludeRuleForAnalysis(SonarLintRuleDefinition ruleDefinition) {
    return !ruleDefinition.getType().equals(RuleType.SECURITY_HOTSPOT) ||
      (globalConfig.isHotspotsEnabled() && serverConnection.permitsHotspotTracking());
  }

  public boolean isSecurityHotspotsDetectionSupported() {
    return serverConnection.permitsHotspotTracking();
  }

  private ServerActiveRule tryConvertDeprecatedKeys(ServerActiveRule possiblyDeprecatedActiveRuleFromStorage) {
    SonarLintRuleDefinition ruleOrTemplateDefinition;
    if (StringUtils.isNotBlank(possiblyDeprecatedActiveRuleFromStorage.getTemplateKey())) {
      ruleOrTemplateDefinition = analysisContext.get().findRule(possiblyDeprecatedActiveRuleFromStorage.getTemplateKey()).orElse(null);
      if (ruleOrTemplateDefinition == null) {
        // The rule template is not known among our loaded analyzers, so return it untouched, to let calling code take appropriate decision
        return possiblyDeprecatedActiveRuleFromStorage;
      }
      var ruleKeyPossiblyWithDeprecatedRepo = RuleKey.parse(possiblyDeprecatedActiveRuleFromStorage.getRuleKey());
      var templateRuleKeyWithCorrectRepo = RuleKey.parse(ruleOrTemplateDefinition.getKey());
      var ruleKey = new RuleKey(templateRuleKeyWithCorrectRepo.repository(), ruleKeyPossiblyWithDeprecatedRepo.rule()).toString();
      return new ServerActiveRule(ruleKey, possiblyDeprecatedActiveRuleFromStorage.getSeverity(), possiblyDeprecatedActiveRuleFromStorage.getParams(),
        ruleOrTemplateDefinition.getKey());
    } else {
      ruleOrTemplateDefinition = analysisContext.get().findRule(possiblyDeprecatedActiveRuleFromStorage.getRuleKey()).orElse(null);
      if (ruleOrTemplateDefinition == null) {
        // The rule is not known among our loaded analyzers, so return it untouched, to let calling code take appropriate decision
        return possiblyDeprecatedActiveRuleFromStorage;
      }
      return new ServerActiveRule(ruleOrTemplateDefinition.getKey(), possiblyDeprecatedActiveRuleFromStorage.getSeverity(), possiblyDeprecatedActiveRuleFromStorage.getParams(),
        null);
    }
  }

  @Override
  public void sync(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, @Nullable ClientProgressMonitor monitor) {
    var result = serverConnection.sync(endpoint, client, projectKeys, new ProgressMonitor(monitor));
    if (result.hasAnalyzerBeenUpdated()) {
      restartAnalysisEngine();
    }
  }

  private void restartAnalysisEngine() {
    var oldAnalysisContext = start();
    oldAnalysisContext.finishGracefully();
  }

  @Override
  public CompletableFuture<ConnectedRuleDetails> getActiveRuleDetails(EndpointParams endpoint, HttpClient client, String ruleKey, @Nullable String projectKey) {
    var ruleDefFromPluginOpt = analysisContext.get().findRule(ruleKey);
    if (ruleDefFromPluginOpt.isPresent()) {
      var ruleDefFromPlugin = ruleDefFromPluginOpt.get();
      if ((!serverConnection.supportsSecretAnalysis() && ruleDefFromPlugin.getLanguage().equals(Language.SECRETS)) || projectKey == null) {
        // if no project key, or secrets are not supported by server and it's a secret rule
        return CompletableFuture.completedFuture(
          new ConnectedRuleDetails(ruleKey, ruleDefFromPlugin.getName(), ruleDefFromPlugin.getHtmlDescription(), ruleDefFromPlugin.getDefaultSeverity(),
            ruleDefFromPlugin.getType(),
            ruleDefFromPlugin.getLanguage(), ""));
      }
    }
    if (projectKey != null) {
      var analyzerConfiguration = serverConnection.getAnalyzerConfiguration(projectKey);
      var storageActiveRule = analyzerConfiguration.getRuleSetByLanguageKey().values().stream()
        .flatMap(s -> s.getRules().stream())
        .filter(r -> tryConvertDeprecatedKeys(r).getRuleKey().equals(ruleKey)).findFirst();
      if (storageActiveRule.isPresent()) {
        var activeRuleFromStorage = storageActiveRule.get();
        var serverSeverity = activeRuleFromStorage.getSeverity();
        if (StringUtils.isNotBlank(activeRuleFromStorage.getTemplateKey())) {
          var templateRuleDefFromPlugin = analysisContext.get().findRule(activeRuleFromStorage.getTemplateKey())
            .orElseThrow(() -> new IllegalStateException("Unable to find rule definition for rule template " + activeRuleFromStorage.getTemplateKey()));
          return new ServerApi(new ServerApiHelper(endpoint, client)).rules().getRule(activeRuleFromStorage.getRuleKey())
            .thenApply(
              serverRule -> new ConnectedRuleDetails(
                ruleKey,
                serverRule.getName(),
                serverRule.getHtmlDesc(),
                serverSeverity,
                templateRuleDefFromPlugin.getType(),
                templateRuleDefFromPlugin.getLanguage(),
                serverRule.getHtmlNote()));
        } else {
          return new ServerApi(new ServerApiHelper(endpoint, client)).rules().getRule(activeRuleFromStorage.getRuleKey())
            .thenApply(serverRule -> ruleDefFromPluginOpt
              .map(ruleDefFromPlugin -> new ConnectedRuleDetails(ruleKey, ruleDefFromPlugin.getName(), ruleDefFromPlugin.getHtmlDescription(),
                Optional.ofNullable(serverSeverity).orElse(ruleDefFromPlugin.getDefaultSeverity()), ruleDefFromPlugin.getType(), ruleDefFromPlugin.getLanguage(),
                serverRule.getHtmlNote()))
              .orElse(new ConnectedRuleDetails(ruleKey, serverRule.getName(), serverRule.getHtmlDesc(),
                Optional.ofNullable(serverSeverity).orElse(serverRule.getSeverity()),
                serverRule.getType(), serverRule.getLanguage(), serverRule.getHtmlNote())));
        }
      }
    }
    throw new IllegalStateException("Unable to find rule details for '" + ruleKey + "'");
  }

  @Override
  public Collection<PluginDetails> getPluginDetails() {
    return analysisContext.get().pluginDetails;
  }

  @Override
  public Map<String, ServerProject> downloadAllProjects(EndpointParams endpoint, HttpClient client, @Nullable ClientProgressMonitor monitor) {
    return wrapErrors(() -> serverConnection.downloadAllProjects(endpoint, client, new ProgressMonitor(monitor)));
  }

  @Override
  public ProjectBranches getServerBranches(String projectKey) {
    var projectBranchesFromStorage = serverConnection.getProjectBranches(projectKey);
    return toApi(projectBranchesFromStorage);
  }

  private static ProjectBranches toApi(org.sonarsource.sonarlint.core.serverconnection.ProjectBranches projectBranchesFromStorage) {
    return new ProjectBranches(projectBranchesFromStorage.getBranchNames(), projectBranchesFromStorage.getMainBranchName());
  }

  @Override
  public List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String branchName, String ideFilePath) {
    return serverConnection.getServerIssues(projectBinding, branchName, ideFilePath);
  }

  @Override
  public List<ServerTaintIssue> getServerTaintIssues(ProjectBinding projectBinding, String branchName, String ideFilePath) {
    return serverConnection.getServerTaintIssues(projectBinding, branchName, ideFilePath);
  }

  @Override
  public List<ServerTaintIssue> getAllServerTaintIssues(ProjectBinding projectBinding, String branchName) {
    return serverConnection.getServerTaintIssues(projectBinding, branchName);
  }

  @Override
  public <G> List<G> getExcludedFiles(ProjectBinding projectBinding, Collection<G> files, Function<G, String> fileIdePathExtractor, Predicate<G> testFilePredicate) {
    AnalyzerConfiguration analyzerConfig;
    try {
      analyzerConfig = serverConnection.getAnalyzerConfiguration(projectBinding.projectKey());
    } catch (StorageException e) {
      LOG.debug("Unable to read settings in local storage", e);
      return List.of();
    }
    var settings = new MapSettings(analyzerConfig.getSettings().getAll());
    var exclusionFilters = new ServerFileExclusions(settings.asConfig());
    exclusionFilters.prepare();

    List<G> excluded = new ArrayList<>();

    for (G file : files) {
      var idePath = fileIdePathExtractor.apply(file);
      if (idePath == null) {
        continue;
      }
      var sqPath = IssueStorePaths.idePathToServerPath(projectBinding, idePath);
      if (sqPath == null) {
        // we can't map it to a SonarQube path, so just apply exclusions to the original ide path
        sqPath = idePath;
      }
      var type = testFilePredicate.test(file) ? Type.TEST : Type.MAIN;
      if (!exclusionFilters.accept(sqPath, type)) {
        excluded.add(file);
      }
    }
    return excluded;
  }

  @Override
  public void subscribeForEvents(EndpointParams endpoint, HttpClient client, Set<String> projectKeys,
    Consumer<ServerEvent> eventConsumer, @Nullable ClientLogOutput clientLogOutput) {
    var logOutput = clientLogOutput == null ? this.logOutput : clientLogOutput;
    if (logOutput == null) {
      logOutput = (message, level) -> {
      };
    }
    serverConnection.subscribeForEvents(endpoint, client, projectKeys, eventConsumer, logOutput);
  }

  @Override
  public void downloadAllServerIssuesForFile(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, String branchName,
    @Nullable ClientProgressMonitor monitor) {
    serverConnection.downloadServerIssuesForFile(endpoint, client, projectBinding, ideFilePath, branchName);
  }

  @Override
  public void downloadAllServerTaintIssuesForFile(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, String branchName,
    @Nullable ClientProgressMonitor monitor) {
    serverConnection.downloadServerTaintIssuesForFile(endpoint, client, projectBinding, ideFilePath, branchName, new ProgressMonitor(monitor));
  }

  @Override
  public void downloadAllServerIssues(EndpointParams endpoint, HttpClient client, String projectKey, String branchName, @Nullable ClientProgressMonitor monitor) {
    serverConnection.downloadServerIssuesForProject(endpoint, client, projectKey, branchName);
  }

  @Override
  public void syncServerIssues(EndpointParams endpoint, HttpClient client, String projectKey, String branchName, @Nullable ClientProgressMonitor monitor) {
    serverConnection.syncServerIssuesForProject(endpoint, client, projectKey, branchName);
  }

  @Override
  public void syncServerTaintIssues(EndpointParams endpoint, HttpClient client, String projectKey, String branchName, @Nullable ClientProgressMonitor monitor) {
    serverConnection.syncServerTaintIssuesForProject(endpoint, client, projectKey, branchName);
  }

  @Override
  public void syncServerHotspots(EndpointParams endpoint, HttpClient client, String projectKey, String branchName, @Nullable ClientProgressMonitor monitor) {
    serverConnection.syncServerHotspotsForProject(endpoint, client, projectKey, branchName);
  }

  @Override
  public void downloadAllServerHotspots(EndpointParams endpoint, HttpClient client, String projectKey, String branchName, @Nullable ClientProgressMonitor monitor) {
    serverConnection.downloadAllServerHotspots(endpoint, client, projectKey, branchName, new ProgressMonitor(monitor));
  }

  @Override
  public void downloadAllServerHotspotsForFile(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath, String branchName,
    @Nullable ClientProgressMonitor monitor) {
    serverConnection.downloadAllServerHotspotsForFile(endpoint, client, projectBinding, ideFilePath, branchName);
  }

  @Override
  public Collection<ServerHotspot> getServerHotspots(ProjectBinding projectBinding, String branchName, String ideFilePath) {
    return serverConnection.getServerHotspots(projectBinding, branchName, ideFilePath);
  }

  @Override
  public ProjectBinding calculatePathPrefixes(String projectKey, Collection<String> ideFilePaths) {
    return serverConnection.calculatePathPrefixes(projectKey, ideFilePaths);
  }

  @Override
  public void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, @Nullable ClientProgressMonitor monitor) {
    requireNonNull(endpoint);
    requireNonNull(projectKey);
    setLogging(null);

    serverConnection.updateProject(endpoint, client, projectKey, new ProgressMonitor(monitor));
  }

  @Override
  public void stop(boolean deleteStorage) {
    setLogging(null);
    try {
      analysisContext.get().destroy();
      serverConnection.stop(deleteStorage);
    } catch (Exception e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private <T> T wrapErrors(Supplier<T> callable) {
    setLogging(null);
    try {
      return callable.get();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private static class AnalysisContext {
    private final Collection<PluginDetails> pluginDetails;
    private final Map<String, SonarLintRuleDefinition> allRulesDefinitionsByKey;
    private final Map<String, String> deprecatedRuleKeysMapping;
    private final AnalysisEngine analysisEngine;

    public AnalysisContext(List<PluginDetails> pluginDetails, Map<String, SonarLintRuleDefinition> allRulesDefinitionsByKey, AnalysisEngine analysisEngine) {
      this.pluginDetails = pluginDetails;
      this.allRulesDefinitionsByKey = allRulesDefinitionsByKey;
      this.analysisEngine = analysisEngine;
      this.deprecatedRuleKeysMapping = allRulesDefinitionsByKey.values().stream()
        .flatMap(r -> r.getDeprecatedKeys().stream().map(dk -> Map.entry(dk, r.getKey())))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void destroy() {
      analysisEngine.stop();
    }

    public void finishGracefully() {
      analysisEngine.finishGracefully();
    }

    public Optional<SonarLintRuleDefinition> findRule(String ruleKey) {
      if (deprecatedRuleKeysMapping.containsKey(ruleKey)) {
        return Optional.of(allRulesDefinitionsByKey.get(deprecatedRuleKeysMapping.get(ruleKey)));
      }
      return Optional.ofNullable(allRulesDefinitionsByKey.get(ruleKey));
    }

  }

}
