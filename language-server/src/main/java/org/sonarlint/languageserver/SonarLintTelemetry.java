/*
 * SonarLint Language Server
 * Copyright (C) 2009-2018 SonarSource SA
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
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.TelemetryClientConfig;
import org.sonarsource.sonarlint.core.client.api.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.telemetry.TelemetryClient;
import org.sonarsource.sonarlint.core.telemetry.TelemetryManager;

public class SonarLintTelemetry {
  public static final String DISABLE_PROPERTY_KEY = "sonarlint.telemetry.disabled";
  private static final Logger LOG = Loggers.get(SonarLintTelemetry.class);

  private final Supplier<ScheduledExecutorService> executorFactory;
  private TelemetryManager telemetry;

  @VisibleForTesting
  ScheduledFuture<?> scheduledFuture;
  private ScheduledExecutorService scheduler;

  public SonarLintTelemetry() {
    this(() -> Executors.newScheduledThreadPool(1));
  }

  public SonarLintTelemetry(Supplier<ScheduledExecutorService> executorFactory) {
    this.executorFactory = executorFactory;
  }

  public void optOut(boolean optOut) {
    if (telemetry != null) {
      if (optOut) {
        if (telemetry.isEnabled()) {
          telemetry.disable();
        }
      } else {
        if (!telemetry.isEnabled()) {
          telemetry.enable();
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
    return telemetry != null && telemetry.isEnabled();
  }

  public void init(@Nullable Path storagePath, String productName, String productVersion) {
    if (storagePath == null) {
      LOG.info("Telemetry disabled because storage path is null");
      return;
    }
    if ("true".equals(System.getProperty(DISABLE_PROPERTY_KEY))) {
      LOG.info("Telemetry disabled by system property");
      return;
    }
    TelemetryClientConfig clientConfig = getTelemetryClientConfig();
    TelemetryClient client = new TelemetryClient(clientConfig, productName, productVersion);
    this.telemetry = newTelemetryManager(storagePath, client);
    try {
      this.scheduler = executorFactory.get();
      this.scheduledFuture = scheduler.scheduleWithFixedDelay(this::upload,
        1, TimeUnit.HOURS.toMinutes(6), TimeUnit.MINUTES);
    } catch (Exception e) {
      if (SonarLintUtils.isInternalDebugEnabled()) {
        LOG.error("Failed scheduling period telemetry job", e);
      }
    }
  }

  TelemetryManager newTelemetryManager(Path path, TelemetryClient client) {
    return new TelemetryManager(path, client);
  }

  @VisibleForTesting
  void upload() {
    if (enabled()) {
      telemetry.uploadLazily();
    }
  }

  public void analysisDoneOnMultipleFiles() {
    if (enabled()) {
      telemetry.analysisDoneOnMultipleFiles();
    }
  }
  
  public void analysisDoneOnSingleFile(@Nullable String fileExtension, int analysisTimeMs) {
    if (enabled()) {
      telemetry.analysisDoneOnSingleFile(fileExtension, analysisTimeMs);
    }
  }

  public void stop() {
    if (enabled()) {
      telemetry.stop();
    }

    if (scheduledFuture != null) {
      scheduledFuture.cancel(false);
      scheduledFuture = null;
    }
    if (scheduler != null) {
      scheduler.shutdown();
    }
  }
}
