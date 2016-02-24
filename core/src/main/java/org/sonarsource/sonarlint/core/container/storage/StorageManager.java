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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;

public class StorageManager {

  private final Path serverStorageRoot;
  private final Path globalStorageRoot;
  private final Path projectStorageRoot;

  public StorageManager(GlobalConfiguration configuration) {
    serverStorageRoot = configuration.getStorageRoot().resolve(configuration.getServerId());
    try {
      Files.createDirectories(serverStorageRoot);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create storage directory: " + serverStorageRoot, e);
    }
    globalStorageRoot = serverStorageRoot.resolve("global");
    try {
      Files.createDirectories(globalStorageRoot);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create global storage directory: " + globalStorageRoot, e);
    }
    projectStorageRoot = serverStorageRoot.resolve("projects");
    try {
      Files.createDirectories(projectStorageRoot);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create project storage directory: " + projectStorageRoot, e);
    }
  }

  public Path getGlobalStorageRoot() {
    return globalStorageRoot;
  }

  public Path getPluginReferencesPath() {
    return globalStorageRoot.resolve("plugin_references.pb");
  }

}
