/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarqube.ws.Components;
import org.sonarqube.ws.Components.Component;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

import static org.sonarsource.sonarlint.core.client.api.util.FileUtils.toSonarQubePath;

public class ModuleHierarchyDownloader {
  static final int PAGE_SIZE = 500;
  private final SonarLintWsClient wsClient;

  public ModuleHierarchyDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  /**
   * Downloads the module hierarchy information starting from a given module key.
   * It returns the relative paths to the given root module for all its sub projects.
   *
   * @param projectKey project for which the hierarchy will be returned.
   * @return Mapping of moduleKey -&gt; relativePath from given module
   */
  public Map<String, String> fetchModuleHierarchy(String projectKey, ProgressWrapper progress) {
    List<Component> modules = new ArrayList<>();

    SonarLintWsClient.getPaginated(wsClient, "api/components/tree.protobuf?qualifiers=BRC&component=" + StringUtils.urlEncode(projectKey),
      Components.TreeWsResponse::parseFrom,
      Components.TreeWsResponse::getPaging,
      Components.TreeWsResponse::getComponentsList,
      modules::add,
      true,
      progress);

    // doesn't include root
    Map<String, Component> modulesByKey = modules.stream().collect(Collectors.toMap(Component::getKey, Function.identity()));

    // component -> ancestorComponent. Doesn't include root
    Map<Component, Component> ancestors = new HashMap<>();
    for (Component c : modules) {
      ancestors.put(c, modulesByKey.get(fetchAncestorKey(c.getKey())));
    }

    // module key -> path from root project base directory
    Map<String, String> modulesWithPath = new HashMap<>();
    modulesWithPath.put(projectKey, "");
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
  private String fetchAncestorKey(String moduleKey) {
    return WsHelperImpl
      .fetchComponent(wsClient, moduleKey)
      .flatMap(r -> r.getAncestorsList().stream().map(Component::getKey).findFirst())
      .orElse(null);
  }
}
