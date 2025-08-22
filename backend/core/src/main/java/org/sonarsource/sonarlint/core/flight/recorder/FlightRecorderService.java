/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.flight.recorder;

import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.util.FailSafeExecutors;

public class FlightRecorderService {

  public static final String SONARLINT_FLIGHT_RECORDER_PERIOD_PROPERTY = "sonarlint.internal.flight.recorder.interval.seconds";
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final int DEFAULT_15_MINUTES = 900;

  private final FlightRecorderStorageService persister;
  private final ScheduledExecutorService flightRecorder = FailSafeExecutors.newSingleThreadScheduledExecutor("Flight Recorder");

  public FlightRecorderService(FlightRecorderStorageService persister) {
    this.persister = persister;
  }

  @PostConstruct
  public void launch() {
    persister.populateSessionInitData(Map.of("param", "1"));
    flightRecorder.scheduleAtFixedRate(this::update, 0,
      Integer.getInteger(SONARLINT_FLIGHT_RECORDER_PERIOD_PROPERTY, DEFAULT_15_MINUTES), TimeUnit.SECONDS);
  }

  @PreDestroy
  public void shutdown() {
    if (!MoreExecutors.shutdownAndAwaitTermination(flightRecorder, 1, TimeUnit.SECONDS)) {
      LOG.warn("Unable to stop flight recorder executor service in a timely manner");
    }
  }

  private void update() {
    // todo actual data https://sonarsource.atlassian.net/browse/SLCORE-1565
    persister.appendData(Clock.systemDefaultZone(), Map.of("param", "1"));
  }
}
