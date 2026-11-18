/*
 * SonarLint Core - Telemetry
 * Copyright (C) SonarSource Sàrl
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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;
import org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryMeasuresBuilderTests {

  @Test
  void it_should_report_sonarqube_cloud_binding_counts_per_region() {
    var serverAttributes = new TelemetryServerAttributes(true, true, 0, 4, 1, 2, 3, false, List.of(), List.of(), null, List.of());
    var liveAttributes = new TelemetryLiveAttributes(serverAttributes, new TelemetryClientLiveAttributesResponse(Map.of()));

    var json = new TelemetryMeasuresBuilder("Linux", "SonarQube for IDE", new TelemetryLocalStorage(), liveAttributes, null, null).build().toJson();

    assertThat(json).contains(
      "{\"key\":\"bindings.cloud_eu_count\",\"value\":\"1\",\"type\":\"integer\",\"granularity\":\"daily\"}",
      "{\"key\":\"bindings.cloud_us_count\",\"value\":\"2\",\"type\":\"integer\",\"granularity\":\"daily\"}",
      "{\"key\":\"bindings.cloud_au_count\",\"value\":\"3\",\"type\":\"integer\",\"granularity\":\"daily\"}");
  }

}
