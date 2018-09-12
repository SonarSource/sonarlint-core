/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.util.ArrayList;
import java.util.List;
import org.sonarqube.ws.WsComponents;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class ProjectFileListDownloader {
  private static final String BASE_PATH = "api/components/tree?qualifiers=FIL&";
  private final SonarLintWsClient wsClient;

  public ProjectFileListDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public List<File> get(String projectKey, ProgressWrapper progress) {
    String path = buildPath(projectKey);
    List<File> files = new ArrayList<>();

    SonarLintWsClient.getPaginated(wsClient, path,
      WsComponents.TreeWsResponse::parseFrom,
      WsComponents.TreeWsResponse::getPaging,
      WsComponents.TreeWsResponse::getComponentsList,
      component -> files.add(new File(component.getKey(), component.getPath())), true, progress);
    return files;
  }

  private String buildPath(String projectKey) {
    String path = BASE_PATH + "component=" + projectKey;
    if (wsClient.getOrganizationKey() != null) {
      path += "&organization=" + StringUtils.urlEncode(wsClient.getOrganizationKey());
    }
    return path;
  }

  public static class File {
    private String key;
    private String path;

    private File(String key, String path) {
      this.key = key;
      this.path = path;
    }

    public String key() {
      return key;
    }

    public String path() {
      return path;
    }
  }
}
