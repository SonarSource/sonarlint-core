/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.objectstore;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

/**
 * An ObjectStore without internal cache that derives the filesystem path to storage using a provided PathMapper.
 *
 * @param <K> type of the key to store by and used when reading back; must be hashable
 * @param <V> type of the value to store
 */
public class SimpleObjectStore<K, V> implements ObjectStore<K, V> {

  private final PathMapper<K> pathMapper;
  private final Reader<V> reader;
  private final Writer<V> writer;

  public SimpleObjectStore(PathMapper<K> pathMapper, Reader<V> reader, Writer<V> writer) {
    this.pathMapper = pathMapper;
    this.reader = reader;
    this.writer = writer;
  }

  @Override
  public Optional<V> read(K key) throws IOException {
    var path = pathMapper.apply(key);
    if (!path.toFile().exists()) {
      return Optional.empty();
    }
    try (var inputStream = Files.newInputStream(path)) {
      return Optional.of(reader.apply(inputStream));
    }
  }

  @Override
  public void delete(K key) throws IOException {
    var path = pathMapper.apply(key);
    Files.deleteIfExists(path);
  }

  @Override
  public void write(K key, V value) throws IOException {
    var path = pathMapper.apply(key);

    var parent = path.getParent();
    if (!parent.toFile().exists()) {
      Files.createDirectories(parent);
    }
    try (var out = Files.newOutputStream(path)) {
      writer.accept(out, value);
    }
  }
}
