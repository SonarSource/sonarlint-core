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
package org.sonarsource.sonarlint.core.languages;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class LanguageSupportRepository {
  public static final String CSHARP_ENTERPRISE_PLUGIN_KEY = "csharpenterprise";
  public static final String CSHARP_OSS_PLUGIN_KEY = "csharp";
  public static final String VBNET_ENTERPRISE_PLUGIN_KEY = "vbnetenterprise";
  public static final String VBNET_OSS_PLUGIN_KEY = "vbnet";
  private static final String GO_ENTERPRISE_PLUGIN_KEY = "goenterprise";
  private static final String IAC_ENTERPRISE_PLUGIN_KEY = "iacenterprise";

  private static final EnumSet<SonarLanguage> LANGUAGES_RAISING_TAINT_VULNERABILITIES =
    EnumSet.of(SonarLanguage.CS, SonarLanguage.JAVA, SonarLanguage.JS, SonarLanguage.TS, SonarLanguage.PHP, SonarLanguage.PYTHON);
  private final EnumSet<SonarLanguage> enabledLanguagesInStandaloneMode;
  private final EnumSet<SonarLanguage> enabledLanguagesInConnectedMode;
  private final Set<String> forceSynchronizedPluginKeys;

  public LanguageSupportRepository(InitializeParams params) {
    this.enabledLanguagesInStandaloneMode = adaptLanguage(params.getEnabledLanguagesInStandaloneMode());
    this.enabledLanguagesInConnectedMode = EnumSet.copyOf(this.enabledLanguagesInStandaloneMode);
    this.enabledLanguagesInConnectedMode.addAll(adaptLanguage(params.getExtraEnabledLanguagesInConnectedMode()));
    this.forceSynchronizedPluginKeys = computeForceSynchronizedPluginKeys(this.enabledLanguagesInConnectedMode);
  }

  private static Set<String> computeForceSynchronizedPluginKeys(Set<SonarLanguage> enabledLanguages) {
    var keys = new HashSet<String>();
    if (enabledLanguages.contains(SonarLanguage.GO)) {
      // SLCORE-1337 Force synchronize "Go Enterprise" before proper repackaging (SQS 2025.2)
      keys.add(GO_ENTERPRISE_PLUGIN_KEY);
    }
    if (enabledLanguages.contains(SonarLanguage.ANSIBLE) || enabledLanguages.contains(SonarLanguage.GITHUBACTIONS)) {
      // Force synchronize "IAC Enterprise" for servers before proper repackaging (SQ 2025.6)
      keys.add(IAC_ENTERPRISE_PLUGIN_KEY);
    }
    if (enabledLanguages.contains(SonarLanguage.CS)) {
      // SLCORE-1179 Force synchronize "C# Enterprise" after repackaging (SQS 10.8+)
      keys.add(CSHARP_ENTERPRISE_PLUGIN_KEY);
      // SLCORE-1898 Synchronize of OSS plugins for dotnet in connected mode, should be removed with SLVS-2778
      keys.add(CSHARP_OSS_PLUGIN_KEY);
    }
    if (enabledLanguages.contains(SonarLanguage.VBNET)) {
      // SLCORE-1179 Force synchronize "VB.NET Enterprise" after repackaging (SQS 10.8+)
      keys.add(VBNET_ENTERPRISE_PLUGIN_KEY);
      // SLCORE-1898 Synchronize of OSS plugins for dotnet in connected mode, should be removed with SLVS-2778
      keys.add(VBNET_OSS_PLUGIN_KEY);
    }
    return Collections.unmodifiableSet(keys);
  }

  public boolean isForceSynchronized(String pluginKey) {
    return forceSynchronizedPluginKeys.contains(pluginKey);
  }

  private static EnumSet<SonarLanguage> adaptLanguage(Set<Language> languagesDto) {
    return languagesDto.stream()
      .map(e -> SonarLanguage.valueOf(e.name()))
      .collect(Collectors.toCollection(() -> EnumSet.noneOf(SonarLanguage.class)));
  }

  public Set<SonarLanguage> getEnabledLanguagesInStandaloneMode() {
    return enabledLanguagesInStandaloneMode;
  }

  public Set<SonarLanguage> getEnabledLanguagesInConnectedMode() {
    return enabledLanguagesInConnectedMode;
  }

  public boolean isEnabledInStandaloneMode(SonarLanguage language) {
    return enabledLanguagesInStandaloneMode.contains(language);
  }

  public boolean isEnabledInConnectedMode(SonarLanguage language) {
    return enabledLanguagesInConnectedMode.contains(language);
  }

  public boolean areTaintVulnerabilitiesSupported() {
    var intersection = EnumSet.copyOf(LANGUAGES_RAISING_TAINT_VULNERABILITIES);
    intersection.retainAll(enabledLanguagesInConnectedMode);
    return !intersection.isEmpty();
  }
}
