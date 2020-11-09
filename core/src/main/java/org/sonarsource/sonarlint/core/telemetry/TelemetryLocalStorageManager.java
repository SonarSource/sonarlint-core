/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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
package org.sonarsource.sonarlint.core.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.function.Consumer;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.util.SonarLintUtils;

/**
 * Serialize and deserialize telemetry data to persistent storage.
 */
class TelemetryLocalStorageManager {
  private static final Logger LOG = Loggers.get(TelemetryLocalStorageManager.class);
  private final Path path;

  TelemetryLocalStorageManager(Path path) {
    this.path = path;
  }

  private void updateAtomically(Consumer<TelemetryLocalStorage> updater) throws IOException {
    Gson gson = createGson();
    Files.createDirectories(path.getParent());
    try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.SYNC);
      FileLock lock = fileChannel.lock()) {
      TelemetryLocalStorage newData = read(gson, fileChannel);

      updater.accept(newData);

      fileChannel.truncate(0);

      String newJson = gson.toJson(newData);
      byte[] encoded = Base64.getEncoder().encode(newJson.getBytes(StandardCharsets.UTF_8));

      fileChannel.write(ByteBuffer.wrap(encoded));
    }
  }

  private TelemetryLocalStorage read(Gson gson, FileChannel fileChannel) throws IOException {
    try {
      final ByteBuffer buf = ByteBuffer.allocate((int) fileChannel.size());
      fileChannel.read(buf);
      byte[] decoded = Base64.getDecoder().decode(buf.array());
      String oldJson = new String(decoded, StandardCharsets.UTF_8);
      TelemetryLocalStorage previousData = gson.fromJson(oldJson, TelemetryLocalStorage.class);

      return TelemetryLocalStorage.validateAndMigrate(previousData);
    } catch (Exception e) {
      if (SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Error reading telemetry data", e);
        throw new IllegalStateException(e);
      }
      return new TelemetryLocalStorage();
    }
  }

  void tryUpdateAtomically(Consumer<TelemetryLocalStorage> updater) {
    try {
      updateAtomically(updater);
    } catch (Exception e) {
      if (SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Error updating telemetry data", e);
        throw new IllegalStateException(e);
      }
    }
  }

  private static Gson createGson() {
    return new GsonBuilder()
      .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter())
      .create();
  }

  private TelemetryLocalStorage load() throws IOException {
    Gson gson = createGson();
    byte[] bytes = Files.readAllBytes(path);
    byte[] decoded = Base64.getDecoder().decode(bytes);
    String json = new String(decoded, StandardCharsets.UTF_8);
    return TelemetryLocalStorage.validateAndMigrate(gson.fromJson(json, TelemetryLocalStorage.class));
  }

  TelemetryLocalStorage tryLoad() {
    try {
      if (!Files.exists(path)) {
        return new TelemetryLocalStorage();
      }
      return load();
    } catch (Exception e) {
      if (SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Error loading telemetry data", e);
        throw new IllegalStateException(e);
      }
      return new TelemetryLocalStorage();
    }
  }
}
