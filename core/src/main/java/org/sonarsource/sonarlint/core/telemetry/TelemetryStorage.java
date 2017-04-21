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
import java.time.LocalDateTime;
import java.util.Base64;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * Holds all data related to telemetry that needs to be persisted. Provides methods to serialize and deserialize to/from files.
 */
class TelemetryStorage {
  private LocalDate installDate;
  private LocalDate lastUseDate;
  private LocalDateTime lastUploadDateTime;
  private Long numUseDays;
  private boolean enabled;

  public TelemetryStorage() {
    enabled = true;
    installDate = LocalDate.now();
    lastUseDate = null;
    lastUploadDateTime = null;
    numUseDays = 0L;
  }

  TelemetryStorage setLastUploadTime(LocalDateTime dateTime) {
    this.lastUploadDateTime = dateTime;
    return this;
  }

  public TelemetryStorage setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  TelemetryStorage setInstallDate(LocalDate installDate) {
    this.installDate = installDate;
    return this;
  }

  TelemetryStorage setLastUseDate(LocalDate lastUseDate) {
    this.lastUseDate = lastUseDate;
    return this;
  }

  TelemetryStorage setNumUseDays(Long numUseDays) {
    this.numUseDays = numUseDays;
    return this;
  }

  public boolean enabled() {
    return enabled;
  }

  @CheckForNull
  public LocalDateTime lastUploadDateTime() {
    return lastUploadDateTime;
  }

  public LocalDate installDate() {
    return installDate;
  }

  @CheckForNull
  public LocalDate lastUseDate() {
    return lastUseDate;
  }

  public Long numUseDays() {
    return numUseDays;
  }

  static TelemetryStorage load(Path filePath) throws IOException {
    Gson gson = new Gson();
    byte[] bytes = Files.readAllBytes(filePath);
    byte[] decoded = Base64.getDecoder().decode(bytes);
    String json = new String(decoded, StandardCharsets.UTF_8);
    return validate(gson.fromJson(json, TelemetryStorage.class));
  }

  public void save(Path filePath) throws IOException {
    Files.createDirectories(filePath.getParent());
    Gson gson = new Gson();
    String json = gson.toJson(this);
    byte[] encoded = Base64.getEncoder().encode(json.getBytes(StandardCharsets.UTF_8));
    Files.write(filePath, encoded);
  }

  public void safeSave(Path filePath) {
    try {
      save(filePath);
    } catch (IOException e) {
      // ignore
    }
  }

  private static TelemetryStorage validate(@Nullable TelemetryStorage telemetry) {
    LocalDate today = LocalDate.now();

    if (telemetry == null) {
      return new TelemetryStorage();
    }

    if (telemetry.installDate() == null || telemetry.installDate().isAfter(today)) {
      telemetry.setInstallDate(today);
    }

    LocalDate lastUseDate = telemetry.lastUseDate();
    if (lastUseDate == null) {
      telemetry.setNumUseDays(0L);
    } else {
      if (lastUseDate.isBefore(telemetry.installDate())) {
        telemetry.setLastUseDate(telemetry.installDate());
      } else if (lastUseDate.isAfter(today)) {
        telemetry.setLastUseDate(today);
      }

      long maxUseDays = telemetry.installDate().until(telemetry.lastUseDate(), DAYS) + 1;
      if (telemetry.numUseDays() > maxUseDays) {
        telemetry.setNumUseDays(maxUseDays);
        telemetry.setLastUseDate(today);
      }
    }

    return telemetry;
  }
}
