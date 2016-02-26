/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class ModuleFinder {

  private final SonarLintWsClient wsClient;

  public ModuleFinder(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public List<RemoteModule> searchModules(String exactKeyOrPartialName) {
    WsResponse response = wsClient.get("api/projects/index?format=json&subprojects=true&key=" + StringUtils.urlEncode(exactKeyOrPartialName));
    DefaultModule[] results = new Gson().fromJson(response.content(), DefaultModule[].class);
    if (results.length > 0) {
      return Arrays.<RemoteModule>asList(results);
    }
    response = wsClient.get("api/projects/index?format=json&subprojects=true&search=" + StringUtils.urlEncode(exactKeyOrPartialName));
    results = new Gson().fromJson(response.content(), DefaultModule[].class);
    return Arrays.<RemoteModule>asList(results);
  }

  private static class DefaultModule implements RemoteModule {

    String k;

    String nm;

    @Override
    public String getKey() {
      return k;
    }

    @Override
    public String getName() {
      return nm;
    }
  }

}
