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
package org.sonarsource.sonarlint.core.serverapi.plugins;

import java.io.InputStream;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

public class PluginsApi {
  private static final Logger LOG = Loggers.get(PluginsApi.class);

  private final ServerApiHelper helper;

  public PluginsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public void getPlugin(String key, ServerApiHelper.IOConsumer<InputStream> pluginFileConsumer) {
    String url = "api/plugins/download?plugin=" + key;
    ServerApiHelper.consumeTimed(
      () -> helper.get(url),
      response -> pluginFileConsumer.accept(response.bodyAsStream()),
      duration -> LOG.info("Downloaded '{}' in {}ms", key, duration));
  }
}
