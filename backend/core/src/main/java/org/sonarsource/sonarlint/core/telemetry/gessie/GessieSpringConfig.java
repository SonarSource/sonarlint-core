/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.telemetry.gessie;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
  GessieService.class,
  GessieHttpClient.class
})
public class GessieSpringConfig {

  public static final String PROPERTY_GESSIE_ENDPOINT = "sonarlint.internal.telemetry.gessie.endpoint";
  public static final String PROPERTY_GESSIE_API_KEY = "sonarlint.internal.telemetry.gessie.api.key";
  private static final String GESSIE_ENDPOINT = "https://events.sonardata.io";
  private static final String IDE_SOURCE = "CiiwpdWnR21rWEOkgJ8tr3EYSXb7dzaQ5ezbipLb";

  @Bean
  String gessieEndpoint() {
    return System.getProperty(PROPERTY_GESSIE_ENDPOINT, GESSIE_ENDPOINT);
  }

  @Bean
  String gessieApiKey() {
    return System.getProperty(PROPERTY_GESSIE_API_KEY, IDE_SOURCE);
  }
}
