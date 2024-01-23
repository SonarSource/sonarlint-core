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
package org.sonarsource.sonarlint.core.file;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import javax.annotation.CheckForNull;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.branch.MatchedSonarProjectBranchChangedEvent;
import org.sonarsource.sonarlint.core.commons.SmartCancelableLoadingCache;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelChecker;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.fs.ClientFile;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.prefix.FileTreeMatcher;
import org.springframework.context.event.EventListener;

import static java.util.stream.Collectors.toList;

/**
 * The path translation service is responsible for matching the files on the server with the files on the client.
 * This is only used in connected mode.
 * A debounce mechanism is used to avoid too many requests to the server.
 */
@Named
@Singleton
public class PathTranslationService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ClientFileSystemService clientFs;
  private final ServerApiProvider serverApiProvider;
  private final ConfigurationRepository configurationRepository;

  private final SmartCancelableLoadingCache<String, FilePathTranslation> cachedPathsTranslationByConfigScope =
    new SmartCancelableLoadingCache<>("sonarlint-path-translation", this::computePaths, (key, oldValue, newValue) -> {
    });

  public PathTranslationService(ClientFileSystemService clientFs, ServerApiProvider serverApiProvider, ConfigurationRepository configurationRepository) {
    this.clientFs = clientFs;
    this.serverApiProvider = serverApiProvider;
    this.configurationRepository = configurationRepository;
  }

  @CheckForNull
  private FilePathTranslation computePaths(String configScopeId, SonarLintCancelChecker cancelChecker) {
    LOG.debug("Computing paths translation for config scope '{}'...", configScopeId);
    var fileMatcher = new FileTreeMatcher();
    var boundScope = configurationRepository.getBoundScope(configScopeId);
    if (boundScope == null) {
      LOG.debug("Config scope '{}' does not exist or is not bound", configScopeId);
      return null;
    }
    var serverApiOpt = serverApiProvider.getServerApi(boundScope.getConnectionId());
    if (serverApiOpt.isEmpty()) {
      LOG.debug("Connection '{}' does not exist", boundScope.getConnectionId());
      return null;
    }
    List<Path> serverFilePaths;
    try {
      serverFilePaths = listAllFilePathsFromServer(serverApiOpt.get(), boundScope.getSonarProjectKey(), cancelChecker);
    } catch (CancellationException e) {
      throw e;
    } catch (Exception e) {
      LOG.debug("Error while getting server file paths for project '{}'", boundScope.getSonarProjectKey(), e);
      return null;
    }
    return matchPaths(boundScope.getConfigScopeId(), fileMatcher, serverFilePaths);
  }

  private FilePathTranslation matchPaths(String configScopeId, FileTreeMatcher fileMatcher, List<Path> serverFilePaths) {
    LOG.debug("Starting matching paths for config scope '{}'...", configScopeId);
    var localFilePaths = clientFs.getFiles(configScopeId);
    if (localFilePaths.isEmpty()) {
      LOG.debug("No client files for config scope '{}'. Skipping path matching.", configScopeId);
      // Maybe a config scope without files, or the filesystem has not been initialized yet
      return new FilePathTranslation(Paths.get(""), Paths.get(""));
    }
    var match = fileMatcher.match(serverFilePaths, localFilePaths.stream().map(ClientFile::getClientRelativePath).collect(toList()));
    LOG.debug("Matched paths for config scope '{}':\n  * idePrefix={}\n  * serverPrefix={}", configScopeId, match.idePrefix(), match.sqPrefix());
    return new FilePathTranslation(match.idePrefix(), match.sqPrefix());
  }

  private static List<Path> listAllFilePathsFromServer(ServerApi serverApi, String projectKey, SonarLintCancelChecker cancelChecker) {
    return serverApi.component().getAllFileKeys(projectKey, cancelChecker).stream()
      .map(fileKey -> StringUtils.substringAfterLast(fileKey, ":"))
      .map(Paths::get)
      .collect(toList());
  }

  @EventListener
  public void onConfigurationScopeRemoved(ConfigurationScopeRemovedEvent event) {
    cachedPathsTranslationByConfigScope.refreshAsync(event.getRemovedConfigurationScopeId());
  }

  @EventListener
  public void onBindingChanged(BindingConfigChangedEvent event) {
    var configScopeId = event.getConfigScopeId();
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
