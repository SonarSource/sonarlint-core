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
package org.sonarsource.sonarlint.core.container.storage;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;

public class ServerStorage implements StorageFolder {
  private final Path folderPath;
  private final RWLock rwLock = new RWLock();

  public ServerStorage(Path folderPath) {
    this.folderPath = folderPath;
  }

  @Override
  public <T> T readAction(Function<Path, T> reader) {
    return rwLock.read(() -> reader.apply(folderPath));
  }

  @Override
  public void writeAction(Consumer<Path> writer) {
    // read here because the lock should only be taken when replacing the whole storage
    rwLock.read(() -> {
      writer.accept(folderPath);
      return null;
    });
  }

  public void replaceStorageWith(Path temp) {
    rwLock.write(() -> {
      Path dest = folderPath;
      FileUtils.deleteRecursively(dest);
      FileUtils.mkdirs(dest.getParent());
      FileUtils.moveDir(temp, dest);
    });
  }
}
