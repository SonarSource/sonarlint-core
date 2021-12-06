/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.client.api.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Serialize and deserialize telemetry data to persistent storage.
 */
class TelemetryLocalStorageManager {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final Path path;

  TelemetryLocalStorageManager(Path path) {
    this.path = path;
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

  private void updateAtomically(Consumer<TelemetryLocalStorage> updater) throws IOException {
    Gson gson = createGson();
    Files.createDirectories(path.getParent());
    try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.SYNC);
      FileLock lock = fileChannel.lock()) {
      TelemetryLocalStorage newData = readAtomically(gson, fileChannel);

      updater.accept(newData);

      writeAtomically(gson, fileChannel, newData);
    }
  }

  private static TelemetryLocalStorage readAtomically(Gson gson, FileChannel fileChannel) throws IOException {
    try {
      if (fileChannel.size() == 0) {
        return new TelemetryLocalStorage();
      }
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

  private static void writeAtomically(Gson gson, FileChannel fileChannel, TelemetryLocalStorage newData) throws IOException {
    fileChannel.truncate(0);

    String newJson = gson.toJson(newData);
    byte[] encoded = Base64.getEncoder().encode(newJson.getBytes(StandardCharsets.UTF_8));

    fileChannel.write(ByteBuffer.wrap(encoded));
  }

  TelemetryLocalStorage tryRead() {
    try {
      if (!Files.exists(path)) {
        return new TelemetryLocalStorage();
      }
      return read();
    } catch (Exception e) {
      if (SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Error loading telemetry data", e);
        throw new IllegalStateException(e);
      }
      return new TelemetryLocalStorage();
    }
  }

  private TelemetryLocalStorage read() throws IOException {
    Gson gson = createGson();
    byte[] bytes = Files.readAllBytes(path);
    byte[] decoded = Base64.getDecoder().decode(bytes);
    String json = new String(decoded, StandardCharsets.UTF_8);
    TelemetryLocalStorage rawData = gson.fromJson(json, TelemetryLocalStorage.class);
    return TelemetryLocalStorage.validateAndMigrate(rawData);
  }

  private static Gson createGson() {
    return new GsonBuilder()
      .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter().nullSafe())
      .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
      .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter().nullSafe())
      .create();
  }

}
