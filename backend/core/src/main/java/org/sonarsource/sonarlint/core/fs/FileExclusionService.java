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
package org.sonarsource.sonarlint.core.fs;

import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.ServerFileExclusions;
import org.sonarsource.sonarlint.core.analysis.api.TriggerType;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MapSettings;
import org.sonarsource.sonarlint.core.commons.SmartCancelableLoadingCache;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.util.FileUtils;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.file.WindowsShortcutUtils;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.FileStatusDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.GetFileExclusionsParams;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfiguration;
import org.sonarsource.sonarlint.core.serverconnection.IssueStorePaths;
import org.sonarsource.sonarlint.core.serverconnection.SonarServerSettingsChangedEvent;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.event.EventListener;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.sonarsource.sonarlint.core.commons.util.git.GitService.createSonarLintGitIgnore;

public class FileExclusionService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  // See org.sonar.api.scan.filesystem.FileExclusions
  private static final Set<String> ALL_EXCLUSION_RELATED_SETTINGS = Set.of(
    CoreProperties.PROJECT_INCLUSIONS_PROPERTY,
    CoreProperties.PROJECT_TEST_INCLUSIONS_PROPERTY,
    CoreProperties.GLOBAL_EXCLUSIONS_PROPERTY,
    CoreProperties.PROJECT_EXCLUSIONS_PROPERTY,
    CoreProperties.GLOBAL_TEST_EXCLUSIONS_PROPERTY,
    CoreProperties.PROJECT_TEST_EXCLUSIONS_PROPERTY);

  private final ConfigurationRepository configRepo;
  private final StorageService storageService;
  private final PathTranslationService pathTranslationService;
  private final ClientFileSystemService clientFileSystemService;
  private final SonarLintRpcClient client;

  private final SmartCancelableLoadingCache<URI, Boolean> serverExclusionByUriCache = new SmartCancelableLoadingCache<>("sonarlint-file-exclusions", this::computeIfExcluded,
    (key, oldValue, newValue) -> {
    });

  public FileExclusionService(ConfigurationRepository configRepo, StorageService storageService, PathTranslationService pathTranslationService,
    ClientFileSystemService clientFileSystemService, SonarLintRpcClient client) {
    this.configRepo = configRepo;
    this.storageService = storageService;
    this.pathTranslationService = pathTranslationService;
    this.clientFileSystemService = clientFileSystemService;
    this.client = client;
  }

  public boolean computeIfExcluded(URI fileUri, SonarLintCancelMonitor cancelMonitor) {
    LOG.debug("Computing file exclusion for uri '{}'", fileUri);
    var clientFile = clientFileSystemService.getClientFile(fileUri);
    if (clientFile == null) {
      LOG.debug("Unable to find client file for uri {}", fileUri);
      return false;
    }
    var configScope = clientFile.getConfigScopeId();
    var effectiveBindingOpt = configRepo.getEffectiveBinding(configScope);
    if (effectiveBindingOpt.isEmpty()) {
      return false;
    }
    var storage = storageService.connection(effectiveBindingOpt.get().connectionId());
    AnalyzerConfiguration analyzerConfig;
    try {
      analyzerConfig = storage.project(effectiveBindingOpt.get().sonarProjectKey()).analyzerConfiguration().read();
    } catch (StorageException e) {
      LOG.debug("Unable to read settings in local storage", e);
      return false;
    }
    var settings = new MapSettings(analyzerConfig.getSettings().getAll());
    var exclusionFilters = new ServerFileExclusions(settings.asConfig());
    exclusionFilters.prepare();
    var idePath = clientFile.getClientRelativePath();
    var pathTranslation = pathTranslationService.getOrComputePathTranslation(configScope);
    Path serverPath;
    if (pathTranslation.isPresent()) {
      serverPath = IssueStorePaths.idePathToServerPath(pathTranslation.get().getIdePathPrefix(), pathTranslation.get().getServerPathPrefix(), idePath);
      if (serverPath == null) {
        // we can't map it to a Sonar server path, so just apply exclusions to the original ide path
        serverPath = idePath;
      }
    } else {
      serverPath = idePath;
    }
    var type = clientFile.isTest() ? InputFile.Type.TEST : InputFile.Type.MAIN;
    var result = !exclusionFilters.accept(serverPath.toString(), type);
    LOG.debug("File exclusion for uri '{}' is {}", fileUri, result);
    return result;
  }

  @EventListener
  public void onBindingChanged(BindingConfigChangedEvent event) {
    if (event.newConfig().isBound()) {
      var connectionId = requireNonNull(event.newConfig().getConnectionId());
      var projectKey = requireNonNull(event.newConfig().getSonarProjectKey());
      // do not recompute exclusions if storage does not yet contain settings (will be done by onFileExclusionSettingsChanged later)
      if (storageService.connection(connectionId).project(projectKey).analyzerConfiguration().isValid()) {
        LOG.debug("Binding changed for config scope '{}', recompute file exclusions...", event.configScopeId());
        clientFileSystemService.getFiles(event.configScopeId()).forEach(f -> serverExclusionByUriCache.refreshAsync(f.getUri()));
      }
    } else {
      LOG.debug("Binding removed for config scope '{}', clearing file exclusions...", event.configScopeId());
      clientFileSystemService.getFiles(event.configScopeId()).forEach(f -> serverExclusionByUriCache.clear(f.getUri()));
    }
  }

  @EventListener
  public void onFileSystemUpdated(FileSystemUpdatedEvent event) {
    event.getRemoved().forEach(f -> serverExclusionByUriCache.clear(f.getUri()));
    // We could try to be more efficient by looking at changed files, and deciding if we need to invalidate or not based on changed
    // attributes (relative path, isTest). But it's probably not worth the effort.
    Stream.concat(event.getAdded().stream(), event.getUpdated().stream())
      .forEach(f -> serverExclusionByUriCache.refreshAsync(f.getUri()));
  }

  @EventListener
  public void onFileExclusionSettingsChanged(SonarServerSettingsChangedEvent event) {
    var settingsDiff = event.getUpdatedSettingsValueByKey();
    if (isFileExclusionSettingsDifferent(settingsDiff)) {
      LOG.debug("File exclusion settings changed, recompute all file exclusions...");
      event.getConfigScopeIds().forEach(configScopeId -> clientFileSystemService.getFiles(configScopeId)
        .forEach(f -> serverExclusionByUriCache.refreshAsync(f.getUri())));
    }
  }

  private static boolean isFileExclusionSettingsDifferent(Map<String, String> updatedSettingsValueByKey) {
    return ALL_EXCLUSION_RELATED_SETTINGS.stream().anyMatch(updatedSettingsValueByKey::containsKey);
  }

  public Map<URI, FileStatusDto> getFilesStatus(Map<String, List<URI>> fileUrisByConfigScope) {
    var existingFileUris = fileUrisByConfigScope.entrySet().stream()
      .flatMap(e -> e.getValue().stream().map(v -> Map.entry(e.getKey(), v)))
      .map(e -> clientFileSystemService.getClientFiles(e.getKey(), e.getValue()))
      .filter(Objects::nonNull)
      .map(ClientFile::getUri)
      .toList();
    return existingFileUris.stream()
      .map(k -> Map.entry(k, serverExclusionByUriCache.get(k)))
      .collect(toMap(Map.Entry::getKey, e -> new FileStatusDto(e.getValue())));
  }

  public boolean isExcluded(URI fileUri) {
    return Boolean.TRUE.equals(serverExclusionByUriCache.get(fileUri));
  }

  public List<ClientFile> refineAnalysisScope(String configScopeId, List<URI> requestedFileUris, TriggerType triggerType, Path baseDir) {
    if (!triggerType.shouldHonorExclusions()) {
      var filteredURIsNoFile = new ArrayList<URI>();
      var filesToAnalyze = requestedFileUris.stream().map(uri -> {
        var file = findFile(configScopeId, uri);
        if (file == null) {
          filteredURIsNoFile.add(uri);
        }
        return file;
      })
        .filter(Objects::nonNull)
        .toList();
      logFilteredURIs("Filtered out URIs having no file", filteredURIsNoFile);
      return filesToAnalyze;
    }
    return filterOutExcludedFiles(configScopeId, baseDir, requestedFileUris);
  }

  private List<ClientFile> filterOutExcludedFiles(String configurationScopeId, Path baseDir, List<URI> files) {
    var sonarLintGitIgnore = createSonarLintGitIgnore(baseDir);
    // INFO: When there are additional filters coming at some point, add them here and log them down below as well!
    var filteredURIsFromExclusionService = new ArrayList<URI>();
    var filteredURIsFromGitIgnore = new ArrayList<URI>();
    var filteredURIsNotUserDefined = new ArrayList<URI>();
    var filteredURIsFromSymbolicLink = new ArrayList<URI>();
    var filteredURIsFromWindowsShortcut = new ArrayList<URI>();
    var filteredURIsNoFile = new ArrayList<URI>();

    // Do the actual filtering and in case of a filtered out URI, save them for later logging!
    var actualFilesToAnalyze = filterOutClientExcludedFiles(configurationScopeId, files)
      .stream()
      .map(uri -> {
        var file = findFile(configurationScopeId, uri);
        if (file == null) {
          filteredURIsNoFile.add(uri);
        }
        return file;
      })
      .filter(Objects::nonNull)
      .filter(file -> {
        if (isExcluded(file.getUri())) {
          filteredURIsFromExclusionService.add(file.getUri());
          return false;
        }
        return true;
      })
      .filter(file -> {
        if (sonarLintGitIgnore.isFileIgnored(file.getClientRelativePath())) {
          filteredURIsFromGitIgnore.add(file.getUri());
          return false;
        }
        return true;
      })
      .filter(file -> {
        if (!file.isUserDefined()) {
          filteredURIsNotUserDefined.add(file.getUri());
          return false;
        }
        return true;
      })
      .filter(file -> {
        // On protocols with schemes like "temp" (used by IntelliJ in the integration tests) or "rse" (the Eclipse Remote System Explorer)
        // and maybe others the check for a symbolic link or Windows shortcut will fail as these file systems cannot be resolved for the
        // operations.
        // If this happens, we won't exclude the file as the chance for someone to use a protocol with such a scheme while also using
        // symbolic links or Windows shortcuts should be near zero and this is less error-prone than excluding the
        try {
          var uri = file.getUri();
          if (Files.isSymbolicLink(FileUtils.getFilePathFromUri(uri))) {
            filteredURIsFromSymbolicLink.add(uri);
            return false;
          } else if (WindowsShortcutUtils.isWindowsShortcut(uri)) {
            filteredURIsFromWindowsShortcut.add(uri);
            return false;
          }
          return true;
        } catch (FileSystemNotFoundException err) {
          LOG.debug("Checking for symbolic links or Windows shortcuts in the file system is not possible for the URI '" + file
            + "'. Therefore skipping the checks due to the underlying protocol / its scheme.", err);
          return true;
        }
      })
      .toList();

    // Log all the filtered out URIs but not for the filters where there were none
    logFilteredURIs("Filtered out URIs based on the exclusion service", filteredURIsFromExclusionService);
    logFilteredURIs("Filtered out URIs ignored by Git", filteredURIsFromGitIgnore);
    logFilteredURIs("Filtered out URIs not user-defined", filteredURIsNotUserDefined);
    logFilteredURIs("Filtered out URIs that are symbolic links", filteredURIsFromSymbolicLink);
    logFilteredURIs("Filtered out URIs that are Windows shortcuts", filteredURIsFromWindowsShortcut);
    logFilteredURIs("Filtered out URIs having no file", filteredURIsNoFile);

    return actualFilesToAnalyze;
  }

  @CheckForNull
  private ClientFile findFile(String configScopeId, URI fileUriToAnalyze) {
    var clientFile = clientFileSystemService.getClientFiles(configScopeId, fileUriToAnalyze);
    if (clientFile == null) {
      LOG.error("File to analyze was not found in the file system: {}", fileUriToAnalyze);
      return null;
    }
    return clientFile;
  }

  private void logFilteredURIs(String reason, ArrayList<URI> uris) {
    if (!uris.isEmpty()) {
      SonarLintLogger.get().debug(reason + ": " + String.join(", ", uris.stream().map(Object::toString).toList()));
    }
  }

  private List<URI> filterOutClientExcludedFiles(String configurationScopeId, List<URI> files) {
    if (isConnectedMode(configurationScopeId)) {
      // client-defined file exclusions only apply in standalone mode
      return files;
    }

    var fileExclusionsGlobPatterns = getClientFileExclusionPatterns(configurationScopeId);
    var matchers = parseGlobPatterns(fileExclusionsGlobPatterns);
    Predicate<URI> fileExclusionFilter = uri -> matchers.stream().noneMatch(matcher -> matcher.matches(Paths.get(uri)));

    return files.stream()
      .filter(fileExclusionFilter)
      .toList();
  }

  private boolean isConnectedMode(String configurationScopeId) {
    return Optional.ofNullable(configRepo.getBindingConfiguration(configurationScopeId))
      .map(BindingConfiguration::isBound)
      .orElse(false);
  }

  private Set<String> getClientFileExclusionPatterns(String configurationScopeId) {
    try {
      return client.getFileExclusions(new GetFileExclusionsParams(configurationScopeId)).join().getFileExclusionPatterns();
    } catch (Exception e) {
      LOG.error("Error when requesting the file exclusions", e);
      return Collections.emptySet();
    }
  }

  private static List<PathMatcher> parseGlobPatterns(Set<String> globPatterns) {
    var fs = FileSystems.getDefault();

    List<PathMatcher> parsedMatchers = new ArrayList<>(globPatterns.size());
    for (String pattern : globPatterns) {
      try {
        parsedMatchers.add(fs.getPathMatcher("glob:" + pattern));
      } catch (Exception e) {
        // ignore invalid patterns, simply skip them
      }
    }
    return parsedMatchers;
  }
}
