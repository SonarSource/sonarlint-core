/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.plugins;

import com.google.gson.Gson;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class PluginsApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerApiHelper helper;

  public PluginsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public List<ServerPlugin> getInstalled() {
    return ServerApiHelper.processTimed(
      () -> helper.get("/api/plugins/installed"),
      response -> {
        var plugins = new Gson().fromJson(response.bodyAsString(), InstalledPluginsPayload.class);
        return Arrays.stream(plugins.plugins).map(PluginsApi::toInstalledPlugin).collect(Collectors.toList());
      },
      duration -> LOG.info("Downloaded plugin list in {}ms", duration));
  }

  private static ServerPlugin toInstalledPlugin(InstalledPluginPayload payload) {
    return new ServerPlugin(payload.key, payload.hash, payload.filename, payload.sonarLintSupported);
  }

  public void getPlugin(String key, ServerApiHelper.IOConsumer<InputStream> pluginFileConsumer) {
    var url = "api/plugins/download?plugin=" + key;
    ServerApiHelper.consumeTimed(
      () -> helper.get(url),
      response -> pluginFileConsumer.accept(response.bodyAsStream()),
      duration -> LOG.info("Downloaded '{}' in {}ms", key, duration));
  }

  private static class InstalledPluginsPayload {
    InstalledPluginPayload[] plugins;
  }

  static class InstalledPluginPayload {
    String key;
    String hash;
    String filename;
    boolean sonarLintSupported;
  }
}
