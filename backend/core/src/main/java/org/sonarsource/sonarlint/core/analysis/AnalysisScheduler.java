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
package org.sonarsource.sonarlint.core.analysis;

import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.NotifyModuleEventCommand;
import org.sonarsource.sonarlint.core.analysis.command.RegisterModuleCommand;
import org.sonarsource.sonarlint.core.analysis.command.UnregisterModuleCommand;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.monitoring.MonitoringService;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.file.WindowsShortcutUtils;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.fs.FileExclusionService;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.rules.RulesRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.ActiveRuleDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.GetAnalysisConfigResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.NodeJsDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.GetInferredAnalysisPropertiesParams;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;
import org.sonarsource.sonarlint.core.rules.RulesService;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;

import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.sonarsource.sonarlint.core.commons.util.git.GitUtils.createSonarLintGitIgnore;

public class AnalysisScheduler {

  private final static SonarLintLogger LOG = SonarLintLogger.get();
  private static final String SONAR_INTERNAL_BUNDLE_PATH_ANALYSIS_PROP = "sonar.js.internal.bundlePath";
  private static final Runnable CANCELING_TERMINATION = () -> {
  };
  private final LogOutput logOutput = SonarLintLogger.get().getTargetForCopy();
  private final AnalysisEngine engine;
  private final LinkedBlockingQueue<AnalysisTask> analysisQueue = new LinkedBlockingQueue<>();
  private final AtomicReference<Runnable> termination = new AtomicReference<>();
  private final AtomicReference<AnalysisTask> executingTask = new AtomicReference<>();
  private final ConfigurationRepository configurationRepository;
  private final NodeJsService nodeJsService;
  private final UserAnalysisPropertiesRepository userAnalysisPropertiesRepository;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;
  private final boolean hotspotEnabled;
  private final StorageService storageService;
  private final PluginsService pluginsService;
  private final RulesRepository rulesRepository;
  private final RulesService rulesService;
  private final LanguageSupportRepository languageSupportRepository;
  private final ClientFileSystemService fileSystemService;
  private final MonitoringService monitoringService;
  private final FileExclusionService fileExclusionService;
  private final ClientFileSystemService clientFileSystemService;
  private final SonarLintRpcClient client;
  private final Path esLintBridgeServerPath;
  private final Thread analysisThread = new Thread(this::executeAnalysisTasks, "sonarlint-analysis-scheduler");

  public AnalysisScheduler(AnalysisEngine engine, ConfigurationRepository configurationRepository, NodeJsService nodeJsService,
    UserAnalysisPropertiesRepository userAnalysisPropertiesRepository, StorageService storageService, PluginsService pluginsService, RulesRepository rulesRepository,
    RulesService rulesService, LanguageSupportRepository languageSupportRepository, ClientFileSystemService fileSystemService, MonitoringService monitoringService,
    FileExclusionService fileExclusionService, ClientFileSystemService clientFileSystemService, SonarLintRpcClient client,
    ConnectionConfigurationRepository connectionConfigurationRepository, boolean hotspotEnabled, Path esLintBridgeServerPath) {
    this.engine = engine;
    this.configurationRepository = configurationRepository;
    this.nodeJsService = nodeJsService;
    this.userAnalysisPropertiesRepository = userAnalysisPropertiesRepository;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
    this.hotspotEnabled = hotspotEnabled;
    this.storageService = storageService;
    this.pluginsService = pluginsService;
    this.rulesRepository = rulesRepository;
    this.rulesService = rulesService;
    this.languageSupportRepository = languageSupportRepository;
    this.fileSystemService = fileSystemService;
    this.monitoringService = monitoringService;
    this.fileExclusionService = fileExclusionService;
    this.clientFileSystemService = clientFileSystemService;
    this.client = client;
    this.esLintBridgeServerPath = esLintBridgeServerPath;

    analysisThread.start();
  }

  private static Path findCommonPrefix(List<URI> uris) {
    var paths = uris.stream().map(Paths::get).toList();
    Path currentPrefixCandidate = paths.get(0).getParent();
    while (currentPrefixCandidate.getNameCount() > 0 && !isPrefixForAll(currentPrefixCandidate, paths)) {
      currentPrefixCandidate = currentPrefixCandidate.getParent();
    }
    return currentPrefixCandidate;
  }

  private static boolean isPrefixForAll(Path prefixCandidate, Collection<Path> paths) {
    return paths.stream().allMatch(p -> p.startsWith(prefixCandidate));
  }

  private static Predicate<? super SonarLintRuleDefinition> isExcludedByConfiguration(Set<String> excludedRules) {
    return r -> {
      if (excludedRules.contains(r.getKey())) {
        return false;
      }
      for (String deprecatedKey : r.getDeprecatedKeys()) {
        if (excludedRules.contains(deprecatedKey)) {
          LOG.warn("Rule '{}' was excluded using its deprecated key '{}'. Please fix your configuration.", r.getKey(), deprecatedKey);
          return false;
        }
      }
      return true;
    };
  }

  private static Predicate<? super SonarLintRuleDefinition> isIncludedByConfiguration(Set<String> includedRules) {
    return r -> {
      if (includedRules.contains(r.getKey())) {
        return true;
      }
      for (String deprecatedKey : r.getDeprecatedKeys()) {
        if (includedRules.contains(deprecatedKey)) {
          LOG.warn("Rule '{}' was included using its deprecated key '{}'. Please fix your configuration.", r.getKey(), deprecatedKey);
          return true;
        }
      }
      return false;
    };
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

  private void executeAnalysisTasks() {
    while (termination.get() == null) {
      SonarLintLogger.get().setTarget(logOutput);
      try {
        executingTask.set(analysisQueue.take());
        if (termination.get() == CANCELING_TERMINATION) {
          executingTask.get().getProgressMonitor().cancel();
          break;
        }
        var task = executingTask.get();
        var analysisConfigForEngine = getAnalysisConfigForEngine(task.getConfigScopeId(), task.getFilePathsToAnalyze(), task.getExtraProperties(), task.isHotspotsOnly());
        var analyzeCommand = new AnalyzeCommand(task.getConfigScopeId(), analysisConfigForEngine, task.getIssueStreamingListener(), logOutput, monitoringService.newTrace(
          "AnalysisService", "analyze"));
        engine.post(analyzeCommand, task.getProgressMonitor())
          .handle((res, err) -> {
            if (err != null) {
              task.getResult().completeExceptionally(err);
            } else {
              task.getResult().complete(res);
            }
            return res;
          });
        executingTask.set(null);
      } catch (InterruptedException e) {
        if (termination.get() != CANCELING_TERMINATION) {
          LOG.error("Analysis engine interrupted", e);
        }
      }
    }
    termination.get().run();
  }

  public CompletableFuture<AnalysisResults> schedule(AnalysisTask task) {
    try {
      analysisQueue.put(task);
      return task.getResult();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return CompletableFuture.failedFuture(e);
    }
  }

  public void finishGracefully() {
    termination.compareAndSet(null, this::honorPendingTasks);
    engine.finishGracefully();
  }

  public void stop() {
    if (!analysisThread.isAlive()) {
      return;
    }
    if (!termination.compareAndSet(null, CANCELING_TERMINATION)) {
      // already terminating
      return;
    }
    var task = executingTask.get();
    if (task != null) {
      task.getProgressMonitor().cancel();
    }
    analysisThread.interrupt();
    List<AnalysisTask> pendingCommands = new ArrayList<>();
    analysisQueue.drainTo(pendingCommands);
    pendingCommands.forEach(c -> c.getResult().cancel(false));
    engine.stop();
  }

  private void honorPendingTasks() {
    // no-op for now, do we need it
  }

  private AnalysisConfiguration getAnalysisConfigForEngine(String configScopeId, List<URI> filePathsToAnalyze, Map<String, String> extraProperties, boolean hotspotsOnly) {
    var analysisConfig = getAnalysisConfig(configScopeId, hotspotsOnly);
    var analysisProperties = analysisConfig.getAnalysisProperties();
    var inferredAnalysisProperties = client.getInferredAnalysisProperties(new GetInferredAnalysisPropertiesParams(configScopeId, filePathsToAnalyze)).join().getProperties();
    analysisProperties.putAll(inferredAnalysisProperties);
    var baseDir = fileSystemService.getBaseDir(configScopeId);
    var actualBaseDir = baseDir == null ? findCommonPrefix(filePathsToAnalyze) : baseDir;
    return AnalysisConfiguration.builder()
      .addInputFiles(toInputFiles(configScopeId, actualBaseDir, filePathsToAnalyze))
      .putAllExtraProperties(analysisProperties)
      // properties sent by client using new API were merged above
      // but this line is important for backward compatibility for clients directly triggering analysis
      .putAllExtraProperties(extraProperties)
      .addActiveRules(analysisConfig.getActiveRules().stream().map(r -> {
        var ar = new ActiveRule(r.getRuleKey(), r.getLanguageKey());
        ar.setParams(r.getParams());
        ar.setTemplateRuleKey(r.getTemplateRuleKey());
        return ar;
      }).toList())
      .setBaseDir(actualBaseDir)
      .build();
  }

  public GetAnalysisConfigResponse getAnalysisConfig(String configScopeId, boolean hotspotsOnly) {
    var bindingOpt = configurationRepository.getEffectiveBinding(configScopeId);
    var activeNodeJs = nodeJsService.getActiveNodeJs();
    var userAnalysisProperties = userAnalysisPropertiesRepository.getUserProperties(configScopeId);
    // if client (IDE) has specified a bundle path, use it
    if (this.esLintBridgeServerPath != null) {
      userAnalysisProperties.put(SONAR_INTERNAL_BUNDLE_PATH_ANALYSIS_PROP, this.esLintBridgeServerPath.toString());
    }
    var nodeJsDetailsDto = activeNodeJs == null ? null : new NodeJsDetailsDto(activeNodeJs.getPath(), activeNodeJs.getVersion().toString());
    return bindingOpt.map(binding -> {
        var serverProperties = storageService.binding(binding).analyzerConfiguration().read().getSettings().getAll();
        var analysisProperties = new HashMap<>(serverProperties);
        analysisProperties.putAll(userAnalysisProperties);
        return new GetAnalysisConfigResponse(buildConnectedActiveRules(binding, hotspotsOnly), analysisProperties, nodeJsDetailsDto,
          Set.copyOf(pluginsService.getConnectedPluginPaths(binding.connectionId())));
      })
      .orElseGet(() -> new GetAnalysisConfigResponse(buildStandaloneActiveRules(), userAnalysisProperties, nodeJsDetailsDto,
        Set.copyOf(pluginsService.getEmbeddedPluginPaths())));
  }

  private List<ActiveRuleDto> buildStandaloneActiveRules() {
    var standaloneRuleConfig = rulesService.getStandaloneRuleConfig();
    Set<String> excludedRules = standaloneRuleConfig.entrySet().stream().filter(not(e -> e.getValue().isActive())).map(Map.Entry::getKey).collect(toSet());
    Set<String> includedRules = standaloneRuleConfig.entrySet().stream().filter(e -> e.getValue().isActive())
      .map(Map.Entry::getKey)
      .filter(r -> !excludedRules.contains(r))
      .collect(toSet());

    var filteredActiveRules = new ArrayList<SonarLintRuleDefinition>();

    var allRulesDefinitions = rulesRepository.getEmbeddedRules().stream()
      .filter(rule -> !rule.getType().equals(RuleType.SECURITY_HOTSPOT))
      .toList();

    filteredActiveRules.addAll(allRulesDefinitions.stream()
      .filter(SonarLintRuleDefinition::isActiveByDefault)
      .filter(isExcludedByConfiguration(excludedRules))
      .toList());
    filteredActiveRules.addAll(allRulesDefinitions.stream()
      .filter(r -> !r.isActiveByDefault())
      .filter(isIncludedByConfiguration(includedRules))
      .toList());

    return filteredActiveRules.stream().map(rd -> {
        Map<String, String> effectiveParams = new HashMap<>(rd.getDefaultParams());
        ofNullable(standaloneRuleConfig.get(rd.getKey())).ifPresent(config -> effectiveParams.putAll(config.getParamValueByKey()));
        // No template rules in standalone mode
        return new ActiveRuleDto(rd.getKey(), rd.getLanguage().getSonarLanguageKey(), effectiveParams, null);
      })
      .toList();
  }

  private List<ActiveRuleDto> buildConnectedActiveRules(Binding binding, boolean hotspotsOnly) {
    var analyzerConfig = storageService.binding(binding).analyzerConfiguration().read();
    var ruleSetByLanguageKey = analyzerConfig.getRuleSetByLanguageKey();
    var result = new ArrayList<ActiveRuleDto>();
    ruleSetByLanguageKey.entrySet()
      .stream().filter(e -> SonarLanguage.forKey(e.getKey()).filter(l -> languageSupportRepository.getEnabledLanguagesInConnectedMode().contains(l)).isPresent())
      .forEach(e -> {
        var languageKey = e.getKey();
        var ruleSet = e.getValue();

        LOG.debug("  * {}: {} active rules", languageKey, ruleSet.getRules().size());
        for (ServerActiveRule possiblyDeprecatedActiveRuleFromStorage : ruleSet.getRules()) {
          var activeRuleFromStorage = tryConvertDeprecatedKeys(binding.connectionId(), possiblyDeprecatedActiveRuleFromStorage);
          SonarLintRuleDefinition ruleOrTemplateDefinition;
          if (StringUtils.isNotBlank(activeRuleFromStorage.getTemplateKey())) {
            ruleOrTemplateDefinition = rulesRepository.getRule(binding.connectionId(), activeRuleFromStorage.getTemplateKey()).orElse(null);
            if (ruleOrTemplateDefinition == null) {
              LOG.debug("Rule {} is enabled on the server, but its template {} is not available in SonarLint", activeRuleFromStorage.getRuleKey(),
                activeRuleFromStorage.getTemplateKey());
              continue;
            }
          } else {
            ruleOrTemplateDefinition = rulesRepository.getRule(binding.connectionId(), activeRuleFromStorage.getRuleKey()).orElse(null);
            if (ruleOrTemplateDefinition == null) {
              LOG.debug("Rule {} is enabled on the server, but not available in SonarLint", activeRuleFromStorage.getRuleKey());
              continue;
            }
          }
          if (shouldIncludeRuleForAnalysis(binding.connectionId(), ruleOrTemplateDefinition, hotspotsOnly)) {
            result.add(buildActiveRuleDto(ruleOrTemplateDefinition, activeRuleFromStorage));
          }
        }
      });
    if (languageSupportRepository.getEnabledLanguagesInConnectedMode().contains(SonarLanguage.IPYTHON)) {
      // Jupyter Notebooks are not yet fully supported in connected mode, use standalone rule configuration in the meantime
      var iPythonRules = buildStandaloneActiveRules()
        .stream().filter(rule -> rule.getLanguageKey().equals(SonarLanguage.IPYTHON.getSonarLanguageKey()))
        .toList();
      result.addAll(iPythonRules);
    }
    return result;
  }

  private List<ClientInputFile> toInputFiles(String configScopeId, Path actualBaseDir, List<URI> fileUrisToAnalyze) {
    var sonarLintGitIgnore = createSonarLintGitIgnore(actualBaseDir);
    // INFO: When there are additional filters coming at some point, add them here and log them down below as well!
    var filteredURIsFromExclusionService = new ArrayList<URI>();
    var filteredURIsFromGitIgnore = new ArrayList<URI>();
    var filteredURIsNotUserDefined = new ArrayList<URI>();
    var filteredURIsFromSymbolicLink = new ArrayList<URI>();
    var filteredURIsFromWindowsShortcut = new ArrayList<URI>();
    var filteredURIsNoInputFile = new ArrayList<URI>();

    // Do the actual filtering and in case of a filtered out URI, save them for later logging!
    var actualFilesToAnalyze = fileUrisToAnalyze.stream()
      .filter(uri -> {
        if (fileExclusionService.isExcluded(uri)) {
          filteredURIsFromExclusionService.add(uri);
          return false;
        }
        return true;
      })
      .filter(uri -> {
        var clientFile = clientFileSystemService.getClientFile(uri);
        if (clientFile == null) {
          LOG.error("File to analyze was not found in the file system: {}", uri);
          return false;
        }
        if (sonarLintGitIgnore.isFileIgnored(clientFile.getClientRelativePath())) {
          filteredURIsFromGitIgnore.add(uri);
          return false;
        }
        return true;
      })
      .filter(uri -> {
        if (!isUserDefined(configScopeId, uri)) {
          filteredURIsNotUserDefined.add(uri);
          return false;
        }
        return true;
      })
      .filter(uri -> {
        // On protocols with schemes like "temp" (used by IntelliJ in the integration tests) or "rse" (the Eclipse Remote System Explorer)
        // and maybe others the check for a symbolic link or Windows shortcut will fail as these file systems cannot be resolved for the
        // operations.
        // If this happens, we won't exclude the file as the chance for someone to use a protocol with such a scheme while also using
        // symbolic links or Windows shortcuts should be near zero and this is less error-prone than excluding the
        try {
          if (Files.isSymbolicLink(Path.of(uri))) {
            filteredURIsFromSymbolicLink.add(uri);
            return false;
          } else if (WindowsShortcutUtils.isWindowsShortcut(uri)) {
            filteredURIsFromWindowsShortcut.add(uri);
            return false;
          }
          return true;
        } catch (FileSystemNotFoundException err) {
          LOG.debug("Checking for symbolic links or Windows shortcuts in the file system is not possible for the URI '" + uri
            + "'. Therefore skipping the checks due to the underlying protocol / its scheme.", err);
          return true;
        }
      })
      .map(uri -> {
        var inputFile = toInputFile(configScopeId, uri);
        if (inputFile == null) {
          filteredURIsNoInputFile.add(uri);
        }
        return inputFile;
      })
      .filter(Objects::nonNull)
      .toList();

    // Log all the filtered out URIs but not for the filters where there were none
    logFilteredURIs("Filtered out URIs based on the exclusion service", filteredURIsFromExclusionService);
    logFilteredURIs("Filtered out URIs ignored by Git", filteredURIsFromGitIgnore);
    logFilteredURIs("Filtered out URIs not user-defined", filteredURIsNotUserDefined);
    logFilteredURIs("Filtered out URIs that are symbolic links", filteredURIsFromSymbolicLink);
    logFilteredURIs("Filtered out URIs that are Windows shortcuts", filteredURIsFromWindowsShortcut);
    logFilteredURIs("Filtered out URIs having no input file", filteredURIsNoInputFile);

    return actualFilesToAnalyze;
  }

  private void logFilteredURIs(String reason, ArrayList<URI> uris) {
    if (!uris.isEmpty()) {
      SonarLintLogger.get().debug(reason + ": " + uris.stream().map(Object::toString).collect(Collectors.joining(", ")));
    }
  }

  @CheckForNull
  private ClientInputFile toInputFile(String configScopeId, URI fileUriToAnalyze) {
    var clientFile = fileSystemService.getClientFiles(configScopeId, fileUriToAnalyze);
    if (clientFile == null) {
      LOG.error("File to analyze was not found in the file system: {}", fileUriToAnalyze);
      return null;
    }
    return new BackendInputFile(clientFile);
  }

  private boolean isUserDefined(String configurationScopeId, URI uri) {
    return ofNullable(fileSystemService.getClientFiles(configurationScopeId, uri))
      .map(ClientFile::isUserDefined)
      .orElse(false);
  }

  private boolean shouldIncludeRuleForAnalysis(String connectionId, SonarLintRuleDefinition ruleDefinition, boolean hotspotsOnly) {
    var isHotspot = ruleDefinition.getType().equals(RuleType.SECURITY_HOTSPOT);
    return (!isHotspot && !hotspotsOnly) || (isHotspot && hotspotEnabled && isHotspotTrackingPossible(connectionId));
  }

  public boolean isHotspotTrackingPossible(String connectionId) {
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    if (connection == null) {
      // Connection is gone
      return false;
    }
    // when storage is not present, consider hotspots should not be detected
    return storageService.connection(connectionId).serverInfo().read().isPresent();
  }

  public ActiveRuleDto buildActiveRuleDto(SonarLintRuleDefinition ruleOrTemplateDefinition, ServerActiveRule activeRule) {
    return new ActiveRuleDto(activeRule.getRuleKey(),
      ruleOrTemplateDefinition.getLanguage().getSonarLanguageKey(),
      getEffectiveParams(ruleOrTemplateDefinition, activeRule),
      trimToNull(activeRule.getTemplateKey()));
  }

  private ServerActiveRule tryConvertDeprecatedKeys(String connectionId, ServerActiveRule possiblyDeprecatedActiveRuleFromStorage) {
    SonarLintRuleDefinition ruleOrTemplateDefinition;
    if (StringUtils.isNotBlank(possiblyDeprecatedActiveRuleFromStorage.getTemplateKey())) {
      ruleOrTemplateDefinition = rulesRepository.getRule(connectionId, possiblyDeprecatedActiveRuleFromStorage.getTemplateKey()).orElse(null);
      if (ruleOrTemplateDefinition == null) {
        // The rule template is not known among our loaded analyzers, so return it untouched, to let calling code take appropriate decision
        return possiblyDeprecatedActiveRuleFromStorage;
      }
      var ruleKeyPossiblyWithDeprecatedRepo = RuleKey.parse(possiblyDeprecatedActiveRuleFromStorage.getRuleKey());
      var templateRuleKeyWithCorrectRepo = RuleKey.parse(ruleOrTemplateDefinition.getKey());
      var ruleKey = new RuleKey(templateRuleKeyWithCorrectRepo.repository(), ruleKeyPossiblyWithDeprecatedRepo.rule()).toString();
      return new ServerActiveRule(ruleKey, possiblyDeprecatedActiveRuleFromStorage.getSeverity(), possiblyDeprecatedActiveRuleFromStorage.getParams(),
        ruleOrTemplateDefinition.getKey(), possiblyDeprecatedActiveRuleFromStorage.getOverriddenImpacts());
    } else {
      ruleOrTemplateDefinition = rulesRepository.getRule(connectionId, possiblyDeprecatedActiveRuleFromStorage.getRuleKey()).orElse(null);
      if (ruleOrTemplateDefinition == null) {
        // The rule is not known among our loaded analyzers, so return it untouched, to let calling code take appropriate decision
        return possiblyDeprecatedActiveRuleFromStorage;
      }
      return new ServerActiveRule(ruleOrTemplateDefinition.getKey(), possiblyDeprecatedActiveRuleFromStorage.getSeverity(), possiblyDeprecatedActiveRuleFromStorage.getParams(),
        null, possiblyDeprecatedActiveRuleFromStorage.getOverriddenImpacts());
    }
  }

  public void registerModule(ClientModuleInfo moduleInfo) {
    engine.post(new RegisterModuleCommand(moduleInfo), new ProgressMonitor(null));
  }

  public void unregisterModule(String scopeId) {
    engine.post(new UnregisterModuleCommand(scopeId), new ProgressMonitor(null));
  }

  public void notifyModuleEvent(String scopeId, ClientFile file, ModuleFileEvent.Type type) {
    engine.post(new NotifyModuleEventCommand(scopeId,
      ClientModuleFileEvent.of(new BackendInputFile(file), type)), new ProgressMonitor(null)).join();
  }
}
