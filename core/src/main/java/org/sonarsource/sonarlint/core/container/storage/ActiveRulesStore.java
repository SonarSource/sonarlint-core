/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
import java.util.Map;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

import static org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths.encodeForFs;

public class ActiveRulesStore {
  public static final String ACTIVE_RULES_FOLDER = "active_rules";
  private static final Logger LOG = Loggers.get(ActiveRulesStore.class);

  private final StorageFolder storageFolder;
  private final RWLock rwLock = new RWLock();

  public ActiveRulesStore(StorageFolder storageFolder) {
    this.storageFolder = storageFolder;
  }

  public void store(Map<String, Sonarlint.ActiveRules> activeRulesBuildersByQProfile) {
    rwLock.write(() -> storageFolder.writeAction(dest -> {
      Path activeRulesDir = dest.resolve(ACTIVE_RULES_FOLDER);
      FileUtils.mkdirs(activeRulesDir);
      for (Map.Entry<String, Sonarlint.ActiveRules> entry : activeRulesBuildersByQProfile.entrySet()) {
        ProtobufUtil.writeToFile(entry.getValue(), activeRulesDir.resolve(encodeForFs(entry.getKey()) + ".pb"));
      }
    }));
  }

  public Sonarlint.ActiveRules getActiveRules(String qualityProfileKey) {
    return rwLock.read(() -> storageFolder.readAction(source -> {
      Path activeRulesPath = getActiveRulesPath(source, qualityProfileKey);
      if (Files.exists(activeRulesPath)) {
        return ProtobufUtil.readFile(activeRulesPath, Sonarlint.ActiveRules.parser());
      } else {
        LOG.info("Unable to find the quality profile {} in the SonarLint storage. You should update the storage, or ignore this message if the profile is empty.",
          qualityProfileKey);
        return Sonarlint.ActiveRules.newBuilder().build();
      }
    }));
  }

  public Path getActiveRulesPath(Path parentPath, String qualityProfileKey) {
    return parentPath.resolve(ACTIVE_RULES_FOLDER).resolve(encodeForFs(qualityProfileKey) + ".pb");
  }

}
