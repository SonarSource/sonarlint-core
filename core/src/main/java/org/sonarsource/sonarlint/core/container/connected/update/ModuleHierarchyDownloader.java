/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarqube.ws.WsComponents;
import org.sonarqube.ws.WsComponents.Component;
import org.sonarqube.ws.WsComponents.ShowWsResponse;
import org.sonarqube.ws.WsComponents.TreeWsResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.util.StringUtils;
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

import static org.sonarsource.sonarlint.core.client.api.util.FileUtils.toSonarQubePath;

public class ModuleHierarchyDownloader {
  static final int PAGE_SIZE = 500;
  private final SonarLintWsClient wsClient;

  public ModuleHierarchyDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  /**
   * Downloads the module hierarchy information starting from a given module key.
   * It returns the relative paths to the given root module for all its sub modules.
   * 
   * @param moduleKey moduleKey for which the hierarchy will be returned.
   * @return Mapping of moduleKey -> relativePath from given module
   */
  public Map<String, String> fetchModuleHierarchy(String moduleKey) {
    List<Component> modules = new ArrayList<>();

    int page = 0;
    TreeWsResponse treeResponse;
    do {
      page++;
      WsResponse response = wsClient.get("api/components/tree.protobuf?ps=" + PAGE_SIZE + "&p=" + page + "&qualifiers=BRC&baseComponentKey=" + StringUtils.urlEncode(moduleKey));
      try (InputStream stream = response.contentStream()) {
        treeResponse = WsComponents.TreeWsResponse.parseFrom(stream);

        modules.addAll(treeResponse.getComponentsList());
      } catch (IOException e) {
        throw new IllegalStateException("Failed to load module hierarchy", e);
      }
    } while (page * PAGE_SIZE < treeResponse.getPaging().getTotal());
    // doesn't include root
    Map<String, Component> modulesById = modules.stream().collect(Collectors.toMap(Component::getId, Function.identity()));

    // component -> ancestorComponent. Doesn't include root
    Map<Component, Component> ancestors = new HashMap<>();
    for (Component c : modules) {
      ancestors.put(c, modulesById.get(fetchAncestorId(c.getId())));
    }

    // module key -> path from root project base directory
    Map<String, String> modulesWithPath = new HashMap<>();
    modulesWithPath.put(moduleKey, "");
    modules.forEach(c -> modulesWithPath.put(c.getKey(), findPathFromRoot(c, ancestors)));

    return modulesWithPath;

  }

  private static String findPathFromRoot(Component component, Map<Component, Component> ancestors) {
    Component c = component;
    Path path = Paths.get("");

    do {
      path = Paths.get(c.getPath()).resolve(path);
      c = ancestors.get(c);
    } while (c != null);

    return toSonarQubePath(path.toString());
  }

  @CheckForNull
  private String fetchAncestorId(String moduleId) {
    WsResponse response = wsClient.get("api/components/show.protobuf?id=" + StringUtils.urlEncode(moduleId));
    try (InputStream stream = response.contentStream()) {
      ShowWsResponse showResponse = WsComponents.ShowWsResponse.parseFrom(stream);
      return showResponse.getAncestorsList().stream().map(Component::getId).findFirst().orElse(null);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load module hierarchy", e);
    }
  }
}
