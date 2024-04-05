/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.tracking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

class StringStoreIndex implements StoreIndex<String> {
  private static final String DEFAULT_INDEX_FILENAME = "index.pb";

  private final Path storeBasePath;
  private final Path indexFilePath;

  public StringStoreIndex(Path storeBasePath) {
    this(storeBasePath, DEFAULT_INDEX_FILENAME);
  }

  protected StringStoreIndex(Path storeBasePath, String indexFileName) {
    this.storeBasePath = storeBasePath;
    this.indexFilePath = storeBasePath.resolve(indexFileName);
  }

  @Override
  public Collection<String> keys() {
    return load().keySet();
  }

  private Map<String, String> load() {
    if (!indexFilePath.toFile().exists()) {
      return Collections.emptyMap();
    }
    try (var stream = Files.newInputStream(indexFilePath)) {
      return Sonarlint.StorageIndex.parseFrom(stream).getMappedPathByKeyMap();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read local issue store index", e);
    }
  }

  @Override
  public void save(String storageKey, Path path) {
    var relativeMappedPath = storeBasePath.relativize(path).toString();
    var builder = Sonarlint.StorageIndex.newBuilder();
    builder.putAllMappedPathByKey(load());
    builder.putMappedPathByKey(storageKey, relativeMappedPath);
    save(builder.build());
  }

  @Override
  public void delete(String storageKey) {
    var builder = Sonarlint.StorageIndex.newBuilder();
    builder.putAllMappedPathByKey(load());
    builder.removeMappedPathByKey(storageKey);
    save(builder.build());
  }

  private void save(Sonarlint.StorageIndex index) {
    try (var stream = Files.newOutputStream(indexFilePath)) {
      index.writeTo(stream);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write local issue store index", e);
    }
  }
}
