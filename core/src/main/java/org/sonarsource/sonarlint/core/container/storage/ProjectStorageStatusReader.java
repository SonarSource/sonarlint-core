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
package org.sonarsource.sonarlint.core.container.storage;

import java.nio.file.Path;
import java.util.Date;
import java.util.function.Function;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.container.model.DefaultProjectStorageStatus;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class ProjectStorageStatusReader implements Function<String, ProjectStorageStatus> {
  private final StoragePaths storageManager;

  public ProjectStorageStatusReader(StoragePaths storageManager) {
    this.storageManager = storageManager;
  }

  @Override
  @CheckForNull
  public ProjectStorageStatus apply(String projectKey) {
    Path updateStatusPath = storageManager.getProjectUpdateStatusPath(projectKey);

    if (updateStatusPath.toFile().exists()) {
      final Sonarlint.StorageStatus statusFromStorage = ProtobufUtil.readFile(updateStatusPath, Sonarlint.StorageStatus.parser());
      final boolean stale = !statusFromStorage.getStorageVersion().equals(StoragePaths.STORAGE_VERSION);
      return new DefaultProjectStorageStatus(new Date(statusFromStorage.getUpdateTimestamp()), stale);
    }
    return null;
  }
}
