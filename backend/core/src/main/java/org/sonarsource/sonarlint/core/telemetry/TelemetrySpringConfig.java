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
package org.sonarsource.sonarlint.core.telemetry;

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
  TelemetryService.class,
  TelemetryManager.class,
  TelemetryLocalStorageManager.class,
  TelemetryHttpClient.class,
  TelemetryServerAttributesProvider.class
})
public class TelemetrySpringConfig {

  public static final String PROPERTY_TELEMETRY_ENDPOINT = "sonarlint.internal.telemetry.endpoint";
  private static final String TELEMETRY_ENDPOINT = "https://telemetry.sonarsource.com/sonarlint";

  @Bean(name = "telemetryPath")
  Path provideTelemetryPath(InitializeParams params, Path userHome) {
    String productKey = params.getTelemetryConstantAttributes().getProductKey();
    return userHome.resolve("telemetry").resolve(productKey).resolve("usage");
  }

  @Bean(name = "telemetryEndpoint")
  String provideTelemetryEndpoint() {
    return System.getProperty(PROPERTY_TELEMETRY_ENDPOINT, TELEMETRY_ENDPOINT);
  }

}
