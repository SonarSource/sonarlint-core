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
package org.sonarsource.sonarlint.core.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.analysis.sonarapi.MultivalueProperty;
import org.sonarsource.sonarlint.core.clientapi.backend.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.clientapi.backend.analysis.GetSupportedFilePatternsParams;
import org.sonarsource.sonarlint.core.clientapi.backend.analysis.GetSupportedFilePatternsResponse;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.serverconnection.StorageFacade;

import static org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.LanguageDetection.sanitizeExtension;

public class AnalysisServiceImpl implements AnalysisService {

  private final ConfigurationRepository configurationRepository;

  private Set<Language> enabledLanguagesInStandaloneMode;
  private Set<Language> enabledLanguagesInConnectedMode;
  private final StorageFacade storageFacade;

  public AnalysisServiceImpl(ConfigurationRepository configurationRepository, StorageFacade storageFacade) {
    this.configurationRepository = configurationRepository;
    this.storageFacade = storageFacade;
  }

  public void initialize(Set<Language> enabledLanguagesInStandaloneMode, Set<Language> enabledLanguagesInConnectedMode) {
    this.enabledLanguagesInStandaloneMode = enabledLanguagesInStandaloneMode;
    this.enabledLanguagesInConnectedMode = enabledLanguagesInConnectedMode;
  }

  @Override
  public CompletableFuture<GetSupportedFilePatternsResponse> getSupportedFilePatterns(GetSupportedFilePatternsParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var configScopeId = params.getConfigScopeId();
      var effectiveBinding = configurationRepository.getEffectiveBinding(configScopeId);
      Set<Language> enabledLanguages;
      Map<String, String> analysisSettings;
      if (effectiveBinding.isEmpty()) {
        enabledLanguages = enabledLanguagesInStandaloneMode;
        analysisSettings = Collections.emptyMap();
      } else {
        enabledLanguages = enabledLanguagesInConnectedMode;
        var projectStorage = storageFacade.projectsStorageFacade(effectiveBinding.get().getConnectionId());
        var analyzerConfiguration = projectStorage.getAnalyzerConfiguration(effectiveBinding.get().getSonarProjectKey());
        analysisSettings = analyzerConfiguration.getSettings().getAll();
      }
      // TODO merge client side analysis settings
      var patterns = getPatterns(enabledLanguages, analysisSettings);
      return new GetSupportedFilePatternsResponse(patterns);
    });
  }

  @NotNull
  private static List<String> getPatterns(Set<Language> enabledLanguages, Map<String, String> analysisSettings) {
    List<String> patterns = new ArrayList<>();

    for (Language language : enabledLanguages) {
      String propertyValue = analysisSettings.get(language.getFileSuffixesPropKey());
      String[] extensions;
      if (propertyValue == null) {
        extensions = language.getDefaultFileSuffixes();
      } else {
        extensions = MultivalueProperty.parseAsCsv(language.getFileSuffixesPropKey(), propertyValue);
      }
      for (String suffix : extensions) {
        var sanitizedExtension = sanitizeExtension(suffix);
        patterns.add("**/*." + sanitizedExtension);
      }
    }
    return patterns;
  }

}
