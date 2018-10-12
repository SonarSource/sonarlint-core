/*
 * SonarLint Language Server
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarlint.languageserver;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * Cache of server connection details. Used for updating server and module storage.
 */
class ServerInfoCache {
  static final String[] SONARCLOUD_ALIAS = {"https://sonarqube.com", "https://www.sonarqube.com",
    "https://www.sonarcloud.io", "https://sonarcloud.io"};
  private final ClientLogger logger;

  private final Map<String, ServerInfo> cache = new HashMap<>();

  ServerInfoCache(ClientLogger logger) {
    this.logger = logger;
  }

  /**
   * Parse the raw value received from client configuration and replace the content of the cache.
   */
  void replace(@Nullable Object servers) {
    cache.clear();
    if (servers == null) {
      return;
    }

    List<Map<String, String>> maps = (List<Map<String, String>>) servers;

    maps.forEach(m -> {
      String serverId = m.get("serverId");
      String url = m.get("serverUrl");
      String token = m.get("token");
      String organization = m.get("organizationKey");

      if (!isBlank(serverId) && !isBlank(url) && !isBlank(token)) {
        cache.put(serverId, new ServerInfo(serverId, url, token, organization));
      } else {
        logger.error(ClientLogger.ErrorType.INCOMPLETE_SERVER_CONFIG);
      }
    });
  }

  boolean isEmpty() {
    return cache.isEmpty();
  }

  boolean containsSonarCloud() {
    return cache.values().stream()
      .anyMatch(s -> isSonarCloudAlias(s.serverUrl));
  }

  private static boolean isSonarCloudAlias(@Nullable String url) {
    return Arrays.asList(SONARCLOUD_ALIAS).contains(url);
  }

  void forEach(BiConsumer<String, ServerInfo> action) {
    cache.forEach(action);
  }

  public ServerInfo get(String serverId) {
    return cache.get(serverId);
  }
}
