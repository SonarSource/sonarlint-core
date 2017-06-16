/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Serialize and deserialize telemetry data to persistent storage.
 */
public class TelemetryStorage {
  private final Path path;

  TelemetryStorage(Path path) {
    this.path = path;
  }

  private void save(TelemetryData data) throws IOException {
    Files.createDirectories(path.getParent());
    Gson gson = new Gson();
    String json = gson.toJson(data);
    byte[] encoded = Base64.getEncoder().encode(json.getBytes(StandardCharsets.UTF_8));
    Files.write(path, encoded);
  }

  void trySave(TelemetryData data) {
    try {
      save(data);
    } catch (Exception e) {
      // ignore
    }
  }

  private TelemetryData load() throws IOException {
    Gson gson = new Gson();
    byte[] bytes = Files.readAllBytes(path);
    byte[] decoded = Base64.getDecoder().decode(bytes);
    String json = new String(decoded, StandardCharsets.UTF_8);
    return TelemetryData.validate(gson.fromJson(json, TelemetryData.class));
  }

  TelemetryData tryLoad() {
    try {
      return load();
    } catch (Exception e) {
      return newDefaultTelemetryData();
    }
  }

  private TelemetryData newDefaultTelemetryData() {
    TelemetryData data = new TelemetryData();
    data.setInstallDate(LocalDate.now());
    data.setEnabled(true);
    return data;
  }
}
