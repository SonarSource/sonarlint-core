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

import java.nio.file.Path;
import java.util.Date;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectId;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.container.model.DefaultModuleStorageStatus;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class ProjectStorageStatusReader {
  private final StorageManager storageManager;

  public ProjectStorageStatusReader(StorageManager storageManager) {
    this.storageManager = storageManager;
  }

  @CheckForNull
  public ProjectStorageStatus readStatus(ProjectId projectId) {
    Path updateStatusPath = storageManager.getProjectUpdateStatusPath(projectId);

    if (updateStatusPath.toFile().exists()) {
      final Sonarlint.StorageStatus statusFromStorage = ProtobufUtil.readFile(updateStatusPath, Sonarlint.StorageStatus.parser());
      final boolean stale = !statusFromStorage.getStorageVersion().equals(StorageManager.STORAGE_VERSION);
      return new DefaultModuleStorageStatus(new Date(statusFromStorage.getUpdateTimestamp()), stale);
    }
    return null;
  }
}
