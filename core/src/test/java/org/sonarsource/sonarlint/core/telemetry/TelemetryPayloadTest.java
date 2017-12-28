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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Test;

public class TelemetryPayloadTest {
  @Test
  public void testGenerationJson() {
    OffsetDateTime installTime = OffsetDateTime.of(2017, 11, 10, 12, 1, 14, 984_123_123, ZoneOffset.ofHours(2));
    OffsetDateTime systemTime = installTime.plusMinutes(1);
    TelemetryPayload m = new TelemetryPayload(60_000, 15, "SLI", "2.4", true, systemTime, installTime);
    String s = m.toJson();

    assertThat(s).isEqualTo("{\"millis_since_installation\":60000,"
      + "\"days_of_use\":15,"
      + "\"sonarlint_version\":\"2.4\","
      + "\"sonarlint_product\":\"SLI\","
      + "\"connected_mode_used\":true,"
      + "\"system_time\":\"2017-11-10T10:02:14.984Z+02:00\","
      + "\"install_time\":\"2017-11-10T10:01:14.984Z+02:00\"}");

    assertThat(m.daysOfUse()).isEqualTo(15);
    assertThat(m.millisSinceInstallation()).isEqualTo(60000);
    assertThat(m.product()).isEqualTo("SLI");
    assertThat(m.version()).isEqualTo("2.4");
    assertThat(m.connectedMode()).isTrue();

  }
}
