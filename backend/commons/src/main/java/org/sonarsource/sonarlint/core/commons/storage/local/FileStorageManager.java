/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.commons.storage.local;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.storage.adapter.LocalDateAdapter;
import org.sonarsource.sonarlint.core.commons.storage.adapter.LocalDateTimeAdapter;
import org.sonarsource.sonarlint.core.commons.storage.adapter.OffsetDateTimeAdapter;

public class FileStorageManager<T extends LocalStorage> {

  public static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Path path;
  private final Gson gson;
  private final Class<T> localStorageType;
  private final Supplier<T> defaultSupplier;
  private T inMemoryStorage;
  private FileTime lastModified;

  public FileStorageManager(Path path, Supplier<T> defaultSupplier, Class<T> localStorageType) {
    this.path = path;
    this.gson = new GsonBuilder()
      .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter().nullSafe())
      .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
      .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter().nullSafe())
      .create();
    this.localStorageType = localStorageType;
    this.defaultSupplier = defaultSupplier;
    this.inMemoryStorage = defaultSupplier.get();
  }

  public T getStorage() {
    if (!path.toFile().exists()) {
      inMemoryStorage = defaultSupplier.get();
      invalidateCache();
    } else if (isCacheInvalid()) {
      refreshInMemoryStorage();
    }
    return inMemoryStorage;
  }

  public boolean isCacheInvalid() {
    try {
      return lastModified == null || !lastModified.equals(Files.getLastModifiedTime(path));
    } catch (IOException e) {
      LOG.warn("Error checking if cache is invalid", e);
      return true;
    }
  }

  public void invalidateCache() {
    lastModified = null;
  }

  public synchronized void refreshInMemoryStorage() {
    try {
      if (isCacheInvalid()) {
        inMemoryStorage = read();
        updateLastModified();
      }
    } catch (Exception e) {
      LOG.warn("Error loading data from the file", e);
    }
  }

  public void updateLastModified() throws IOException {
    lastModified = Files.getLastModifiedTime(path);
  }

  public T read() throws IOException {
    try (var fileChannel = FileChannel.open(path, StandardOpenOption.READ)) {
      return read(fileChannel);
    }
  }

  public void tryUpdateAtomically(Consumer<T> updater) {
    try {
      updateAtomically(updater);
    } catch (Exception e) {
      invalidateCache();
      LOG.warn("Error updating data in the file", e);
    }
  }

  private synchronized void updateAtomically(Consumer<T> updater) throws IOException {
    Files.createDirectories(path.getParent());
    try (var fileChannel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.SYNC);
         var ignored = fileChannel.lock()) {
      var storageData = read(fileChannel);
      updater.accept(storageData);
      storageData.validateAndMigrate();
      writeAtomically(fileChannel, storageData);
      inMemoryStorage = storageData;
    }
    updateLastModified();
  }

  public T read(FileChannel fileChannel) {
    try {
      if (fileChannel.size() == 0) {
        return defaultSupplier.get();
      }
      final var buf = ByteBuffer.allocate((int) fileChannel.size());
      fileChannel.read(buf);
      var decoded = Base64.getDecoder().decode(buf.array());
      var oldJson = new String(decoded, StandardCharsets.UTF_8);
      var localStorage = gson.fromJson(oldJson, localStorageType);
      localStorage.validateAndMigrate();

      return localStorage;
    } catch (Exception e) {
      LOG.warn("Error reading data from file", e);
      return defaultSupplier.get();
    }
  }

  private void writeAtomically(FileChannel fileChannel, T newData) throws IOException {
    fileChannel.truncate(0);

    var newJson = gson.toJson(newData);
    var encoded = Base64.getEncoder().encode(newJson.getBytes(StandardCharsets.UTF_8));

    fileChannel.write(ByteBuffer.wrap(encoded));
  }
}
