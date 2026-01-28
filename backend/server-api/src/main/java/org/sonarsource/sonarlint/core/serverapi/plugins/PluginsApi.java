/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.plugins;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class PluginsApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String API_PLUGINS_INSTALLED_PATH = "/api/plugins/installed";

  private final ServerApiHelper helper;

  public PluginsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public List<ServerPlugin> getInstalled(SonarLintCancelMonitor cancelMonitor) {
    var start = System.currentTimeMillis();
    var plugins = helper.isSonarCloud()
      ? helper.getAnonymousJson(API_PLUGINS_INSTALLED_PATH, InstalledPluginsPayloadDto.class, cancelMonitor)
      : helper.getJson(API_PLUGINS_INSTALLED_PATH, InstalledPluginsPayloadDto.class, cancelMonitor);
    var result = Arrays.stream(plugins.plugins()).map(PluginsApi::toInstalledPlugin).toList();
    var duration = System.currentTimeMillis() - start;
    LOG.info("Downloaded plugin list in {}ms", duration);
    return result;
  }

  private static ServerPlugin toInstalledPlugin(InstalledPluginsPayloadDto.InstalledPluginPayloadDto payload) {
    return new ServerPlugin(payload.key(), payload.hash(), payload.filename(), payload.sonarLintSupported());
  }

  public void getPlugin(String key, Consumer<InputStream> pluginFileConsumer, SonarLintCancelMonitor cancelMonitor) {
    var url = "api/plugins/download?plugin=" + key;
    var start = System.currentTimeMillis();
    try (var response = get(url, cancelMonitor)) {
      pluginFileConsumer.accept(response.bodyAsStream());
      var duration = System.currentTimeMillis() - start;
      LOG.info("Downloaded '{}' in {}ms", key, duration);
    }
  }

  private HttpClient.Response get(String path, SonarLintCancelMonitor cancelMonitor) {
    return helper.isSonarCloud() ? helper.getAnonymous(path, cancelMonitor) : helper.get(path, cancelMonitor);
  }

}
