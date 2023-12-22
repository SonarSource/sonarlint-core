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
package org.sonarsource.sonarlint.core.fs;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.ServerFileExclusions;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MapSettings;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.file.PathTranslationService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.FileStatusDto;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfiguration;
import org.sonarsource.sonarlint.core.serverconnection.IssueStorePaths;
import org.sonarsource.sonarlint.core.serverconnection.SonarServerSettingsChangedEvent;
import org.sonarsource.sonarlint.core.serverconnection.storage.StorageException;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.event.EventListener;

import static java.util.stream.Collectors.toMap;

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

  private final LoadingCache<URI, Boolean> serverExclusionByUri = Caffeine.newBuilder()
    .build(this::computeExclusion);

  public FileExclusionService(ConfigurationRepository configRepo, StorageService storageService, PathTranslationService pathTranslationService,
    ClientFileSystemService clientFileSystemService) {
    this.configRepo = configRepo;
    this.storageService = storageService;
    this.pathTranslationService = pathTranslationService;
    this.clientFileSystemService = clientFileSystemService;
  }

  public boolean computeExclusion(URI fileUri) {
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
    var storage = storageService.getStorageFacade().connection(effectiveBindingOpt.get().getConnectionId());
    AnalyzerConfiguration analyzerConfig;
    try {
      analyzerConfig = storage.project(effectiveBindingOpt.get().getSonarProjectKey()).analyzerConfiguration().read();
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
    return !exclusionFilters.accept(serverPath.toString(), type);
  }

  @EventListener
  public void onBindingChanged(BindingConfigChangedEvent event) {
    clientFileSystemService.getFiles(event.getConfigScopeId()).forEach(f -> serverExclusionByUri.invalidate(f.getUri()));
  }

  @EventListener
  public void onFileSystemUpdated(FileSystemUpdatedEvent event) {
    event.getRemoved().forEach(f -> serverExclusionByUri.invalidate(f.getUri()));
    // We could try to be more efficient by looking at changed files, and deciding if we need to invalidate or not based on changed
    // attributes (relative path, isTest). But it's probably not worth the effort.
    event.getAddedOrUpdated().forEach(f -> serverExclusionByUri.invalidate(f.getUri()));
  }

  private static boolean isFileExclusionSettingsDifferent(Map<String, String> updatedSettingsValueByKey) {
    return ALL_EXCLUSION_RELATED_SETTINGS.stream().anyMatch(updatedSettingsValueByKey::containsKey);
  }

  @EventListener
  public void onFileExclusionSettingsChanged(SonarServerSettingsChangedEvent event) {
    var settingsDiff = event.getUpdatedSettingsValueByKey();
    if (isFileExclusionSettingsDifferent(settingsDiff)) {
      event.getConfigScopeIds().forEach(configScopeId -> clientFileSystemService.getFiles(configScopeId)
        .forEach(f -> serverExclusionByUri.invalidate(f.getUri())));
    }
  }

  public Map<URI, FileStatusDto> getFilesStatus(Map<String, List<URI>> fileUrisByConfigScope) {
    var existingFileUris = fileUrisByConfigScope.entrySet().stream()
      .flatMap(e -> e.getValue().stream().map(v -> Map.entry(e.getKey(), v)))
      .map(e -> clientFileSystemService.getClientFiles(e.getKey(), e.getValue()))
      .filter(Objects::nonNull)
      .map(ClientFile::getUri)
      .collect(Collectors.toList());
    return serverExclusionByUri.getAll(existingFileUris)
      .entrySet()
      .stream()
      .collect(toMap(Map.Entry::getKey, e -> new FileStatusDto(e.getValue())));
  }
}
