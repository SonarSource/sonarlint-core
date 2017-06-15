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
package org.sonarsource.sonarlint.core.container.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.sonarsource.sonarlint.core.plugin.PluginIndex;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

/**
 * List of plugins is in the local storage
 */
public class StoragePluginIndexProvider implements PluginIndex {

  private StoragePaths storageManager;

  public StoragePluginIndexProvider(StoragePaths storageManager) {
    this.storageManager = storageManager;
  }

  @Override
  public List<PluginReference> references() {
    Path pluginReferencesPath = storageManager.getPluginReferencesPath();
    if (!Files.exists(pluginReferencesPath)) {
      return Collections.emptyList();
    }
    Sonarlint.PluginReferences protoReferences = ProtobufUtil.readFile(pluginReferencesPath, Sonarlint.PluginReferences.parser());
    return protoReferences.getReferenceList().stream()
      .map(r -> new PluginReference(r.getHash(), r.getFilename()))
      .collect(Collectors.toList());
  }
}
