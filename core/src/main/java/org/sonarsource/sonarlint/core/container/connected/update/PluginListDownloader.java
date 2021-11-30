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

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.model.DefaultSonarAnalyzer;
import org.sonarsource.sonarlint.core.plugin.common.Language;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class PluginListDownloader {

  private static final String OLD_SONARTS_PLUGIN_KEY = "typescript";

  private static final Logger LOG = Loggers.get(PluginListDownloader.class);

  public static final String WS_PATH = "/api/plugins/installed";

  private final ServerApiHelper serverApiHelper;
  private final PluginVersionChecker pluginVersionChecker;
  private final Set<Language> enabledLanguages;

  public PluginListDownloader(ConnectedGlobalConfiguration globalConfiguration, ServerApiHelper serverApiHelper, PluginVersionChecker pluginVersionChecker) {
    this.serverApiHelper = serverApiHelper;
    this.pluginVersionChecker = pluginVersionChecker;

    this.enabledLanguages = globalConfiguration.getEnabledLanguages();
  }

  public List<SonarAnalyzer> downloadPluginList() {
    return ServerApiHelper.processTimed(
      () -> serverApiHelper.get(WS_PATH),
      response -> {
        InstalledPlugins installedPlugins = new Gson().fromJson(response.bodyAsString(), InstalledPlugins.class);
        return Arrays.stream(installedPlugins.plugins).map(this::toSonarAnalyzer).collect(Collectors.toList());
      },
      duration -> LOG.info("Downloaded plugin list in {}ms", duration));
  }

  private SonarAnalyzer toSonarAnalyzer(InstalledPlugin plugin) {
    boolean sonarlintCompatible = (!isKnownSonarSourceAnalyzer(plugin.key) || providesAtLeastOneEnabledLanguage(plugin.key)) && plugin.sonarLintSupported;
    DefaultSonarAnalyzer sonarAnalyzer = new DefaultSonarAnalyzer(plugin.key, plugin.filename, plugin.hash, sonarlintCompatible);
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

  private static class InstalledPlugins {
    InstalledPlugin[] plugins;
  }

  static class InstalledPlugin {
    String key;
    String hash;
    String filename;
    boolean sonarLintSupported;
    long updatedAt;
  }
}
