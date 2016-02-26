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
package org.sonarsource.sonarlint.core.container.storage;

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.util.FileUtils;

public class StorageManager {

  public static final String PLUGIN_REFERENCES_PB = "plugin_references.pb";
  public static final String PROPERTIES_PB = "properties.pb";
  public static final String RULES_PB = "rules.pb";
  public static final String ACTIVE_RULES_FOLDER = "active_rules";
  private final Path serverStorageRoot;
  private final Path globalStorageRoot;
  private final Path projectStorageRoot;

  public StorageManager(GlobalConfiguration configuration) {
    serverStorageRoot = configuration.getStorageRoot().resolve(configuration.getServerId());
    FileUtils.forceMkDirs(serverStorageRoot);
    globalStorageRoot = serverStorageRoot.resolve("global");
    FileUtils.forceMkDirs(globalStorageRoot);
    projectStorageRoot = serverStorageRoot.resolve("projects");
    FileUtils.forceMkDirs(projectStorageRoot);

    // TODO Check storage status for current server
  }

  public Path getGlobalStorageRoot() {
    return globalStorageRoot;
  }

  public Path getPluginReferencesPath() {
    return globalStorageRoot.resolve(PLUGIN_REFERENCES_PB);
  }

  public Path getGlobalPropertiesPath() {
    return globalStorageRoot.resolve(PROPERTIES_PB);
  }

  public Path getRulesPath() {
    return globalStorageRoot.resolve(RULES_PB);
  }

  public Path getActiveRulesPath(String qProfileKey) {
    return globalStorageRoot.resolve(ACTIVE_RULES_FOLDER).resolve(qProfileKey + ".pb");
  }

}
