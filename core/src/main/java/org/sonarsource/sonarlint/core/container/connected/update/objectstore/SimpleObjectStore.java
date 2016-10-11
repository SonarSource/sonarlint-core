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
package org.sonarsource.sonarlint.core.container.connected.update.objectstore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An ObjectStore that keeps the mapping of keys to filesystem paths in memory.
 *
 * Warning: the class does not persist the mapping of keys to paths.
 * When the process exits, the mapping between keys and paths is non-recoverable.
 *
 * @param <K> type of the key to store by and used when reading back
 * @param <V> type of the value to store
 */
public class SimpleObjectStore<K, V> implements ObjectStore<K, V> {

  private final Map<K, Path> index = new HashMap<>();

  private final PathGenerator pathGenerator;
  private final Reader<V> reader;
  private final Writer<V> writer;

  public SimpleObjectStore(PathGenerator pathGenerator, Reader<V> reader, Writer<V> writer) {
    this.pathGenerator = pathGenerator;
    this.reader = reader;
    this.writer = writer;
  }

  @Override
  public Optional<V> read(K key) throws IOException {
    Path path = index.get(key);
    if (path == null) {
      return Optional.empty();
    }
    try (InputStream in = Files.newInputStream(path)) {
      return Optional.of(reader.parseFrom(in));
    }
  }

  @Override
  public void write(K key, V value) throws IOException {
    Path path = index.get(key);
    if (path == null) {
      path = pathGenerator.next();
      index.put(key, path);

      File parentFile = path.toFile().getParentFile();
      if (!parentFile.isDirectory() && !parentFile.mkdirs()) {
        throw new IOException("could not create directory: " + parentFile);
      }
    }
    try (OutputStream out = Files.newOutputStream(path)) {
      writer.writeTo(out, value);
    }
  }
}
