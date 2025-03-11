/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry.payload;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresDimension;
import org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresPayload;
import org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValueGranularity.DAILY;
import static org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValueType.INTEGER;

class TelemetryMeasuresPayloadTests {

  @Test
  void testGenerationJson() {
    var messageUuid = "25318599-9aec-4e1d-a535-1bfa4f7fcf39";
    var installTime = OffsetDateTime.of(2017, 11, 10, 12, 1, 14, 984_123_123, ZoneOffset.ofHours(2));
    var measures = generateMeasures();

    var m = new TelemetryMeasuresPayload(
      messageUuid,
      "Linux Ubuntu 24.04",
      installTime,
      "SonarQube for IDE",
      TelemetryMeasuresDimension.INSTALLATION,
      measures
    );

    var s = m.toJson();

    assertThat(s).isEqualTo("{" +
      "\"message_uuid\":\"25318599-9aec-4e1d-a535-1bfa4f7fcf39\"," +
      "\"os\":\"Linux Ubuntu 24.04\"," +
      "\"install_time\":\"2017-11-10T12:01:14.984+02:00\"," +
      "\"sonarlint_product\":\"SonarQube for IDE\"," +
      "\"dimension\":\"installation\"," +
      "\"metric_values\":[{" +
      "\"key\":\"shared_connected_mode.manual\",\"value\":\"1\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"shared_connected_mode.imported\",\"value\":\"2\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"shared_connected_mode.auto\",\"value\":\"3\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"shared_connected_mode.exported\",\"value\":\"4\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"help_and_feedback.doc_link\",\"value\":\"5\",\"type\":\"integer\",\"granularity\":\"daily\"" +
      "}]" +
      "}");

    assertThat(m.messageUuid()).isEqualTo(messageUuid);
    assertThat(m.os()).isEqualTo("Linux Ubuntu 24.04");
    assertThat(m.installTime()).isEqualTo(installTime);
    assertThat(m.product()).isEqualTo("SonarQube for IDE");
    assertThat(m.dimension()).isEqualTo(TelemetryMeasuresDimension.INSTALLATION);
    assertValues(m.values());
  }

  private List<TelemetryMeasuresValue> generateMeasures() {
    var values = new ArrayList<TelemetryMeasuresValue>();

    values.add(new TelemetryMeasuresValue("shared_connected_mode.manual", String.valueOf(1), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("shared_connected_mode.imported", String.valueOf(2), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("shared_connected_mode.auto", String.valueOf(3), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("shared_connected_mode.exported", String.valueOf(4), INTEGER, DAILY));

    values.add(new TelemetryMeasuresValue("help_and_feedback.doc_link", String.valueOf(5), INTEGER, DAILY));

    return values;
  }

  private static void assertValues(List<TelemetryMeasuresValue> values) {
    assertThat(values).extracting("key", "value", "type", "granularity")
      .contains(tuple("shared_connected_mode.manual", "1", INTEGER, DAILY))
      .contains(tuple("shared_connected_mode.imported", "2", INTEGER, DAILY))
      .contains(tuple("shared_connected_mode.auto", "3", INTEGER, DAILY))
      .contains(tuple("shared_connected_mode.exported", "4", INTEGER, DAILY))
      .contains(tuple("help_and_feedback.doc_link", "5", INTEGER, DAILY));
  }

}
