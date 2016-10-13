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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.function.Function;

import javax.annotation.CheckForNull;

import org.sonarsource.sonarlint.core.client.api.connected.ModuleUpdateStatus;
import org.sonarsource.sonarlint.core.container.model.DefaultModuleUpdateStatus;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class ModuleUpdateStatusReader implements Function<String, ModuleUpdateStatus> {
  private final StorageManager storageManager;

  public ModuleUpdateStatusReader(StorageManager storageManager) {
    this.storageManager = storageManager;
  }

  @Override
  @CheckForNull
  public ModuleUpdateStatus apply(String moduleKey) {
    Path updateStatusPath = storageManager.getModuleUpdateStatusPath(moduleKey);

    if (Files.exists(updateStatusPath)) {
      final Sonarlint.UpdateStatus updateStatusFromStorage = ProtobufUtil.readFile(updateStatusPath, Sonarlint.UpdateStatus.parser());
      final boolean stale = (updateStatusFromStorage.getSonarlintCoreVersion() == null) ||
        !updateStatusFromStorage.getSonarlintCoreVersion().equals(VersionUtils.getLibraryVersion());
      return new DefaultModuleUpdateStatus(new Date(updateStatusFromStorage.getUpdateTimestamp()), stale);
    }
    return null;
  }
}
