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

public interface StorageFolder {
  <T> T readAction(Function<Path, T> reader);

  void writeAction(Consumer<Path> writer);

  class Default implements StorageFolder {
    private final Path folderPath;

    public Default(Path folderPath) {
      this.folderPath = folderPath;
    }

    @Override
    public <T> T readAction(Function<Path, T> reader) {
      return reader.apply(folderPath);
    }

    @Override
    public void writeAction(Consumer<Path> writer) {
      writer.accept(folderPath);
    }
  }
}
