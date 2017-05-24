/*
 * SonarLint Language Server
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
package org.sonarlint.languageserver;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.telemetry.Telemetry;

public class SonarLintTelemetry {
  public static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";
  private static final Logger LOG = LoggerFactory.getLogger(SonarLintTelemetry.class);

  private boolean enabled;
  private Telemetry telemetryEngine;

  @VisibleForTesting
  ScheduledFuture<?> scheduledFuture;
  private ScheduledExecutorService scheduler;

  public SonarLintTelemetry() {
    this.telemetryEngine = null;
  }

  public void optOut(boolean optOut) {
    if (telemetryEngine != null) {
      if (optOut == !telemetryEngine.enabled()) {
        return;
      }
      telemetryEngine.enable(!optOut);
      if (optOut) {
        try {
          TelemetryClientConfig clientConfig = getTelemetryClientConfig();
          telemetryEngine.getClient().optOut(clientConfig, isAnyProjectConnected());
        } catch (Exception e) {
          // fail silently
        }
      }
    }
  }

  private static TelemetryClientConfig getTelemetryClientConfig() {
    return new TelemetryClientConfig.Builder()
      .userAgent("SonarLint")
      .build();
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean optedIn() {
    return enabled && this.telemetryEngine.enabled();
  }

  public void init(Path storagePath, String productName, String productVersion) {
    if ("true".equals(System.getProperty(DISABLE_PROPERTY_KEY))) {
      this.enabled = false;
      LOG.info("Telemetry disabled by system property");
      return;
    }
    try {
      this.telemetryEngine = newTelemetry(storagePath, productName, productVersion);
      scheduler = Executors.newScheduledThreadPool(1);
      this.scheduledFuture = scheduler.scheduleWithFixedDelay(this::upload,
        1, TimeUnit.HOURS.toMinutes(6), TimeUnit.MINUTES);
      this.enabled = true;
    } catch (Exception e) {
      // fail silently
      enabled = false;
    }
  }

  protected Telemetry newTelemetry(Path storagePath, String productName, String productVersion) throws Exception {
    return new Telemetry(storagePath, productName, productVersion);
  }

  private void upload() {
    if (enabled) {
      TelemetryClientConfig clientConfig = getTelemetryClientConfig();
      telemetryEngine.getClient().tryUpload(clientConfig, isAnyProjectConnected());
    }
  }

  public void analysisSubmitted() {
    if (enabled) {
      telemetryEngine.getDataCollection().analysisDone();
    }
  }

  public void stop() {
    try {
      if (telemetryEngine != null) {
        telemetryEngine.save();
      }
    } catch (IOException e) {
      // ignore
    }
    if (scheduledFuture != null) {
      scheduledFuture.cancel(false);
      scheduledFuture = null;
    }
    if (scheduler != null) {
      scheduler.shutdown();
    }
  }

  private static boolean isAnyProjectConnected() {
    return false;
  }
}
