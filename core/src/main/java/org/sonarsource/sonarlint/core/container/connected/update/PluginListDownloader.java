/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.container.model.DefaultSonarAnalyzer;
import org.sonarsource.sonarlint.core.plugin.commons.PluginsMinVersions;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.plugins.InstalledPlugin;
import org.sonarsource.sonarlint.core.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class PluginListDownloader {

  private static final String OLD_SONARTS_PLUGIN_KEY = "typescript";

  private final PluginsApi pluginsApi;

  private final PluginsMinVersions pluginVersionChecker;
  private final Set<Language> enabledLanguages;

  public PluginListDownloader(ConnectedGlobalConfiguration globalConfiguration, ServerApiHelper serverApiHelper, PluginsMinVersions pluginVersionChecker) {
    this.pluginsApi = new ServerApi(serverApiHelper).plugins();
    this.pluginVersionChecker = pluginVersionChecker;
    this.enabledLanguages = globalConfiguration.getEnabledLanguages();
  }

  public List<SonarAnalyzer> downloadPluginList() {
    return pluginsApi.getInstalled().stream().map(this::toSonarAnalyzer).collect(Collectors.toList());
  }

  private SonarAnalyzer toSonarAnalyzer(InstalledPlugin plugin) {
    boolean sonarlintCompatible = (!isKnownSonarSourceAnalyzer(plugin.getKey()) || providesAtLeastOneEnabledLanguage(plugin.getKey())) && plugin.isSonarLintSupported();
    var sonarAnalyzer = new DefaultSonarAnalyzer(plugin.getKey(), plugin.getFilename(), plugin.getHash(), sonarlintCompatible);
    checkMinVersion(sonarAnalyzer);
    return sonarAnalyzer;
  }

  private boolean providesAtLeastOneEnabledLanguage(String pluginKey) {
    // Special case for old TS plugin
    if (OLD_SONARTS_PLUGIN_KEY.equals(pluginKey)) {
      return enabledLanguages.contains(Language.TS);
    }
    return enabledLanguages.stream().anyMatch(language -> pluginKey.equals(language.getPluginKey()));
  }

  private static boolean isKnownSonarSourceAnalyzer(String pluginKey) {
    // Special case for old TS plugin
    return OLD_SONARTS_PLUGIN_KEY.equals(pluginKey) || Language.containsPlugin(pluginKey);
  }

  private void checkMinVersion(DefaultSonarAnalyzer analyzer) {
    String minVersion = pluginVersionChecker.getMinimumVersion(analyzer.key());
    analyzer.minimumVersion(minVersion);
    analyzer.version(VersionUtils.getJarVersion(analyzer.filename()));
    analyzer.versionSupported(pluginVersionChecker.isVersionSupported(analyzer.key(), analyzer.version()));
  }
}
