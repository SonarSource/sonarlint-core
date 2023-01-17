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
package org.sonarsource.sonarlint.core.telemetry;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.clientapi.backend.telemetry.GetStatusResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.telemetry.TelemetryService;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class TelemetryServiceImpl implements TelemetryService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";

  @CheckForNull
  private TelemetryLocalStorageManager telemetryLocalStorageManager;

  public void initialize(String productKey, Path sonarlintUserHome) {
    if (isDisabledBySystemProperty()) {
      LOG.info("Telemetry disabled by system property");
      return;
    }
    this.telemetryLocalStorageManager = new TelemetryLocalStorageManager(TelemetryPathManager.getPath(sonarlintUserHome, productKey));
  }

  private static boolean isDisabledBySystemProperty() {
    return "true".equals(System.getProperty(DISABLE_PROPERTY_KEY));
  }

  private TelemetryLocalStorageManager getTelemetryLocalStorageManager() {
    if (telemetryLocalStorageManager == null) {
      throw new IllegalStateException("Telemetry service has not been initialized");
    }
    return telemetryLocalStorageManager;
  }

  @Override
  public CompletableFuture<GetStatusResponse> getStatus() {
    return CompletableFuture.completedFuture(new GetStatusResponse(isEnabled()));
  }

  private boolean isEnabled() {
    return !isDisabledBySystemProperty() && telemetryLocalStorageManager != null && telemetryLocalStorageManager.tryRead().enabled();
  }

  public void hotspotOpenedInBrowser() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::incrementOpenHotspotInBrowserCount);
    }
  }


  public void showHotspotRequestReceived() {
    if (isEnabled()) {
      getTelemetryLocalStorageManager().tryUpdateAtomically(TelemetryLocalStorage::incrementShowHotspotRequestCount);
    }
  }
}
