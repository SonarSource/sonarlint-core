/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class TelemetryPayloadTest {
  @Test
  public void testGenerationJson() {
    OffsetDateTime installTime = OffsetDateTime.of(2017, 11, 10, 12, 1, 14, 984_123_123, ZoneOffset.ofHours(2));
    OffsetDateTime systemTime = installTime.plusMinutes(1);
    TelemetryAnalyzerPerformancePayload[] perf = new TelemetryAnalyzerPerformancePayload[1];
    Map<String, BigDecimal> distrib = new LinkedHashMap<>();
    distrib.put("0-300", BigDecimal.valueOf(9.90));
    distrib.put("1000-2000", BigDecimal.valueOf(90.10));
    perf[0] = new TelemetryAnalyzerPerformancePayload("java", distrib);
    TelemetryPayload m = new TelemetryPayload(4, 15, "SLI", "2.4", true, true, systemTime, installTime, perf);
    String s = m.toJson();

    assertThat(s).isEqualTo("{\"days_since_installation\":4,"
      + "\"days_of_use\":15,"
      + "\"sonarlint_version\":\"2.4\","
      + "\"sonarlint_product\":\"SLI\","
      + "\"connected_mode_used\":true,"
      + "\"connected_mode_sonarcloud\":true,"
      + "\"system_time\":\"2017-11-10T12:02:14.984+02:00\","
      + "\"install_time\":\"2017-11-10T12:01:14.984+02:00\""
      + ",\"analyses\":[{\"language\":\"java\",\"rate_per_duration\":{\"0-300\":9.9,\"1000-2000\":90.1}}]}");

    assertThat(m.daysOfUse()).isEqualTo(15);
    assertThat(m.daysSinceInstallation()).isEqualTo(4);
    assertThat(m.product()).isEqualTo("SLI");
    assertThat(m.version()).isEqualTo("2.4");
    assertThat(m.connectedMode()).isTrue();
    assertThat(m.analyses()).hasSize(1);
    assertThat(m.connectedModeSonarcloud()).isTrue();
    assertThat(m.systemTime()).isEqualTo(systemTime);
  }
}
