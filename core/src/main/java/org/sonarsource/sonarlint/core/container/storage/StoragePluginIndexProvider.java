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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.sonarsource.sonarlint.core.plugin.PluginIndexProvider;

/**
 * List of plugins is in local storage
 */
public class StoragePluginIndexProvider implements PluginIndexProvider {

  private StorageManager storageManager;

  public StoragePluginIndexProvider(StorageManager storageManager) {
    this.storageManager = storageManager;
  }

  @Override
  public List<PluginReference> references() {
    Path pluginReferencesPath = storageManager.getPluginReferencesPath();
    if (!Files.exists(pluginReferencesPath)) {
      return Collections.emptyList();
    }
    org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences protoReferences = ProtobufUtil.readFile(pluginReferencesPath,
      org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.parser());
    return Lists.transform(protoReferences.getReferenceList(), new Function<org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference, PluginReference>() {
      @Override
      public PluginReference apply(org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference input) {
        return new PluginReference().setHash(input.getHash()).setFilename(input.getFilename());
      }
    });
  }
}
