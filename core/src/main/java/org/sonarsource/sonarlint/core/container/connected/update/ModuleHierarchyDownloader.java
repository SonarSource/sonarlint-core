/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.container.connected.ProgressWrapperAdapter;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.component.ComponentPath;
import org.sonarsource.sonarlint.core.serverapi.component.ComponentApi;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.sonarsource.sonarlint.core.client.api.util.FileUtils.toSonarQubePath;

public class ModuleHierarchyDownloader {
  static final int PAGE_SIZE = 500;
  private final ComponentApi componentApi;

  public ModuleHierarchyDownloader(ServerApiHelper serverApiHelper) {
    this.componentApi = new ServerApi(serverApiHelper).component();
  }

  /**
   * Downloads the module hierarchy information starting from a given module key.
   * It returns the relative paths to the given root module for all its sub projects.
   *
   * @param projectKey project for which the hierarchy will be returned.
   * @return Mapping of moduleKey -&gt; relativePath from given module
   */
  public Map<String, String> fetchModuleHierarchy(String projectKey, ProgressWrapper progress) {
    List<ComponentPath> modules = componentApi.getSubProjects(projectKey, new ProgressWrapperAdapter(progress));

    // doesn't include root
    Map<String, ComponentPath> modulesByKey = modules.stream().collect(Collectors.toMap(ComponentPath::getKey, Function.identity()));

    // component -> ancestorComponent. Doesn't include root
    Map<ComponentPath, ComponentPath> ancestors = new HashMap<>();
    for (ComponentPath c : modules) {
      ancestors.put(c, modulesByKey.get(componentApi.fetchFirstAncestorKey(c.getKey()).orElse(null)));
    }

    // module key -> path from root project base directory
    Map<String, String> modulesWithPath = new HashMap<>();
    modulesWithPath.put(projectKey, "");
    modules.forEach(c -> modulesWithPath.put(c.getKey(), findPathFromRoot(c, ancestors)));

    return modulesWithPath;
  }

  private static String findPathFromRoot(ComponentPath component, Map<ComponentPath, ComponentPath> ancestors) {
    var c = component;
    var path = Paths.get("");

    do {
      path = Paths.get(c.getPath()).resolve(path);
      c = ancestors.get(c);
    } while (c != null);

    return toSonarQubePath(path.toString());
  }
}
