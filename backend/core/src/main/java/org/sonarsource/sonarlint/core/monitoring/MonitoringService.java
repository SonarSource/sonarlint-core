/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.monitoring;

import io.sentry.Sentry;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.SystemUtils;
import org.sonarsource.sonarlint.core.commons.SonarLintCoreVersion;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

@Named
@Singleton
public class MonitoringService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private InitializeParams initializeParams;
  private DogfoodEnvironmentDetectionService dogfoodEnvDetectionService;

  public MonitoringService(InitializeParams initializeParams, DogfoodEnvironmentDetectionService dogfoodEnvDetectionService) {
    this.initializeParams = initializeParams;
    this.dogfoodEnvDetectionService = dogfoodEnvDetectionService;

    this.init();
  }

  public void init() {
    var productKey = initializeParams.getTelemetryConstantAttributes().getProductKey();
    var environment = "dogfood";
    var sonarQubeForIDEVersion = initializeParams.getTelemetryConstantAttributes().getProductVersion();
    var ideVersion = initializeParams.getTelemetryConstantAttributes().getIdeVersion();
    var releaseVersion = SonarLintCoreVersion.get();
    var platform = SystemUtils.OS_NAME;
    var architecture = SystemUtils.OS_ARCH;

    if (dogfoodEnvDetectionService.isDogfoodEnvironment()) {
      LOG.info("Initializing Sentry");
      Sentry.init(sentryOptions -> {
        sentryOptions.setDsn("https://ad1c1fe3cb2b12fc2d191ecd25f89866@o1316750.ingest.us.sentry.io/4508201175089152");
        sentryOptions.setRelease(releaseVersion);
        sentryOptions.setEnvironment(environment);
        sentryOptions.setTag("productKey", productKey);
        sentryOptions.setTag("sonarQubeForIDEVersion", sonarQubeForIDEVersion);
        sentryOptions.setTag("ideVersion", ideVersion);
        sentryOptions.setTag("platform", platform);
        sentryOptions.setTag("architecture", architecture);
      });
    }
  }
}
