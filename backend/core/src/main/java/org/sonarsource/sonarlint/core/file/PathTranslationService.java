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
package org.sonarsource.sonarlint.core.file;

import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.SonarLintMDC;
import org.sonarsource.sonarlint.core.branch.MatchedSonarProjectBranchChangedEvent;
import org.sonarsource.sonarlint.core.commons.SmartCancelableLoadingCache;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverconnection.prefix.FileTreeMatcher;
import org.springframework.context.event.EventListener;

/**
 * The path translation service is responsible for matching the files on the server with the files on the client.
 * This is only used in connected mode.
 * A debounce mechanism is used to avoid too many requests to the server.
 */
public class PathTranslationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ClientFileSystemService clientFs;
  private final ConfigurationRepository configurationRepository;
  private final ServerFilePathsProvider serverFilePathsProvider;
  private final SmartCancelableLoadingCache<String, FilePathTranslation> cachedPathsTranslationByConfigScope =
    new SmartCancelableLoadingCache<>("sonarlint-path-translation", this::computePaths, (key, oldValue, newValue) -> {
    });

  public PathTranslationService(ClientFileSystemService clientFs, ConfigurationRepository configurationRepository, ServerFilePathsProvider serverFilePathsProvider) {
    this.clientFs = clientFs;
    this.configurationRepository = configurationRepository;
    this.serverFilePathsProvider = serverFilePathsProvider;
  }

  @CheckForNull
  private FilePathTranslation computePaths(String configScopeId, SonarLintCancelMonitor cancelMonitor) {
    SonarLintMDC.putConfigScopeId(configScopeId);
    LOG.debug("Computing paths translation for config scope '{}'...", configScopeId);
    var fileMatcher = new FileTreeMatcher();
    var binding = configurationRepository.getEffectiveBinding(configScopeId).orElse(null);
    if (binding == null) {
      LOG.debug("Config scope '{}' does not exist or is not bound", configScopeId);
      return null;
    }
    return serverFilePathsProvider.getServerPaths(binding, cancelMonitor)
      .map(paths -> matchPaths(configScopeId, fileMatcher, paths))
      .orElse(null);
  }

  private FilePathTranslation matchPaths(String configScopeId, FileTreeMatcher fileMatcher, List<Path> serverFilePaths) {
    LOG.debug("Starting matching paths for config scope '{}'...", configScopeId);
    // Use directories instead of files for path matching optimization
    var localDirectories = clientFs.getDirectories(configScopeId);
    if (localDirectories.isEmpty()) {
      LOG.debug("No client directories for config scope '{}'. Skipping path matching.", configScopeId);
      // Maybe a config scope without files, or the filesystem has not been initialized yet
      return new FilePathTranslation(Paths.get(""), Paths.get(""));
    }
    // Convert server file paths to directory paths
    var serverDirectories = serverFilePaths.stream()
      .map(Path::getParent)
      .filter(java.util.Objects::nonNull)
      .distinct()
      .toList();
    var match = fileMatcher.match(serverDirectories, localDirectories);
    LOG.debug("Matched paths for config scope '{}':\n  * idePrefix={}\n  * serverPrefix={}", configScopeId, match.idePrefix(), match.sqPrefix());
    return new FilePathTranslation(match.idePrefix(), match.sqPrefix());
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    cachedPathsTranslationByConfigScope.clear(event.getRemovedConfigurationScopeId());
  }

  @EventListener
  public void onBindingChanged(BindingConfigChangedEvent event) {
    var configScopeId = event.configScopeId();
    cachedPathsTranslationByConfigScope.refreshAsync(configScopeId);
  }

  @EventListener
  public void onBranchChanged(MatchedSonarProjectBranchChangedEvent event) {
    var configScopeId = event.getConfigurationScopeId();
    cachedPathsTranslationByConfigScope.refreshAsync(configScopeId);
  }

  public Optional<FilePathTranslation> getOrComputePathTranslation(String configurationScopeId) {
    return Optional.ofNullable(cachedPathsTranslationByConfigScope.get(configurationScopeId));
  }

  @PreDestroy
  public void shutdown() {
    cachedPathsTranslationByConfigScope.close();
  }
}
