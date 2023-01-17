/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Serialize and deserialize telemetry data to persistent storage.
 */
public class TelemetryLocalStorageManager {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final Path path;
  private final Gson gson;

  public TelemetryLocalStorageManager(Path path) {
    this.path = path;
    this.gson = new GsonBuilder()
      .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter().nullSafe())
      .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
      .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter().nullSafe())
      .create();
  }

  public void tryUpdateAtomically(Consumer<TelemetryLocalStorage> updater) {
    try {
      updateAtomically(updater);
    } catch (Exception e) {
      if (InternalDebug.isEnabled()) {
        LOG.error("Error updating telemetry data", e);
        throw new IllegalStateException(e);
      }
    }
  }

  private synchronized void updateAtomically(Consumer<TelemetryLocalStorage> updater) throws IOException {
    Files.createDirectories(path.getParent());
    try (var fileChannel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.SYNC);
      var lock = fileChannel.lock()) {
      var newData = readAtomically(fileChannel);

      updater.accept(newData);

      writeAtomically(fileChannel, newData);
    }
  }

  private TelemetryLocalStorage readAtomically(FileChannel fileChannel) throws IOException {
    try {
      if (fileChannel.size() == 0) {
        return new TelemetryLocalStorage();
      }
      final var buf = ByteBuffer.allocate((int) fileChannel.size());
      fileChannel.read(buf);
      var decoded = Base64.getDecoder().decode(buf.array());
      var oldJson = new String(decoded, StandardCharsets.UTF_8);
      var previousData = gson.fromJson(oldJson, TelemetryLocalStorage.class);

      return TelemetryLocalStorage.validateAndMigrate(previousData);
    } catch (Exception e) {
      if (InternalDebug.isEnabled()) {
        LOG.error("Error reading telemetry data", e);
        throw new IllegalStateException(e);
      }
      return new TelemetryLocalStorage();
    }
  }

  private void writeAtomically(FileChannel fileChannel, TelemetryLocalStorage newData) throws IOException {
    fileChannel.truncate(0);

    var newJson = gson.toJson(newData);
    var encoded = Base64.getEncoder().encode(newJson.getBytes(StandardCharsets.UTF_8));

    fileChannel.write(ByteBuffer.wrap(encoded));
  }

  public TelemetryLocalStorage tryRead() {
    try {
      if (!Files.exists(path)) {
        return new TelemetryLocalStorage();
      }
      return read();
    } catch (Exception e) {
      if (InternalDebug.isEnabled()) {
        LOG.error("Error loading telemetry data", e);
        throw new IllegalStateException(e);
      }
      return new TelemetryLocalStorage();
    }
  }

  private TelemetryLocalStorage read() throws IOException {
    var bytes = Files.readAllBytes(path);
    var decoded = Base64.getDecoder().decode(bytes);
    var json = new String(decoded, StandardCharsets.UTF_8);
    var rawData = gson.fromJson(json, TelemetryLocalStorage.class);
    return TelemetryLocalStorage.validateAndMigrate(rawData);
  }

}
