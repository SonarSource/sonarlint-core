/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry;

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.storage.local.FileStorageManager;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryMigrationDto;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Serialize and deserialize telemetry data to persistent storage.
 */
public class TelemetryLocalStorageManager {

  private final FileStorageManager<TelemetryLocalStorage> fileStorageManager;
  @Nullable
  private final TelemetryMigrationDto telemetryMigration;

  public TelemetryLocalStorageManager(@Qualifier("telemetryPath") Path telemetryPath, InitializeParams initializeParams) {
    fileStorageManager = new FileStorageManager<>(telemetryPath, TelemetryLocalStorage::new, TelemetryLocalStorage.class);
    this.telemetryMigration = initializeParams.getTelemetryMigration();
  }

  @VisibleForTesting
  TelemetryLocalStorage tryRead() {
    return getStorage();
  }

  private TelemetryLocalStorage getStorage() {
    var inMemoryStorage = fileStorageManager.getStorage();
    applyTelemetryMigration(inMemoryStorage);
    return inMemoryStorage;
  }

  private void applyTelemetryMigration(TelemetryLocalStorage inMemoryStorage) {
    if (needToMigrateTelemetry(inMemoryStorage)) {
      inMemoryStorage.setEnabled(telemetryMigration.isEnabled());
      inMemoryStorage.setInstallTime(telemetryMigration.getInstallTime());
      inMemoryStorage.setNumUseDays(telemetryMigration.getNumUseDays());
    }
  }

  private boolean needToMigrateTelemetry(TelemetryLocalStorage inMemoryStorage) {
    if (telemetryMigration == null) {
      return false;
    }
    var duration = Duration.between(inMemoryStorage.installTime(), OffsetDateTime.now());
    return duration.getSeconds() < 10 && inMemoryStorage.numUseDays() == 0;
  }

  public void tryUpdateAtomically(Consumer<TelemetryLocalStorage> updater) {
    fileStorageManager.tryUpdateAtomically(updater);
  }

  public LocalDateTime lastUploadTime() {
    return getStorage().lastUploadTime();
  }

  public boolean isEnabled() {
    return getStorage().enabled();
  }

  public OffsetDateTime installTime() {
    return getStorage().installTime();
  }
}
