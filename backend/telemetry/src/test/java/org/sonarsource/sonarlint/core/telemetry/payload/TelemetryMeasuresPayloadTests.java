/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry.payload;

import com.google.gson.Gson;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.McpTransportMode;
import org.sonarsource.sonarlint.core.telemetry.TelemetryConnectionAttributes;
import org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresDimension;
import org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresPayload;
import org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValueGranularity.DAILY;
import static org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValueType.BOOLEAN;
import static org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValueType.INTEGER;
import static org.sonarsource.sonarlint.core.telemetry.measures.payload.TelemetryMeasuresValueType.STRING;

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
      "{\"key\":\"new_bindings.manual\",\"value\":\"1\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"new_bindings.accepted_suggestion_remote_url\",\"value\":\"2\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"new_bindings.accepted_suggestion_properties_file\",\"value\":\"3\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"new_bindings.accepted_suggestion_shared_config_file\",\"value\":\"4\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"new_bindings.accepted_suggestion_project_name\",\"value\":\"5\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"binding_suggestion_clue.remote_url\",\"value\":\"5\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"bindings.child_count\",\"value\":\"1\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"bindings.server_count\",\"value\":\"2\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"bindings.cloud_eu_count\",\"value\":\"0\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"bindings.cloud_us_count\",\"value\":\"0\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"connections.attributes\",\"value\":\"[{\\\"userId\\\":\\\"user-id\\\",\\\"organizationId\\\":\\\"org-id\\\"}]\",\"type\":\"string\",\"granularity\":\"daily\"}," +
      "{\"key\":\"help_and_feedback.doc_link\",\"value\":\"5\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"analysis_reporting.trigger_count_vcs_changed_files\",\"value\":\"7\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"performance.biggest_size_config_scope_files\",\"value\":\"12345\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"automatic_analysis.enabled\",\"value\":\"true\",\"type\":\"boolean\",\"granularity\":\"daily\"}," +
      "{\"key\":\"automatic_analysis.toggled_count\",\"value\":\"1\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"mcp.configuration_requested\",\"value\":\"3\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"mcp.integration_enabled\",\"value\":\"true\",\"type\":\"boolean\",\"granularity\":\"daily\"}," +
      "{\"key\":\"mcp.transport_mode\",\"value\":\"HTTP\",\"type\":\"string\",\"granularity\":\"daily\"}," +
      "{\"key\":\"ide_labs.joined\",\"value\":\"true\",\"type\":\"boolean\",\"granularity\":\"daily\"}," +
      "{\"key\":\"ide_labs.enabled\",\"value\":\"true\",\"type\":\"boolean\",\"granularity\":\"daily\"}," +
      "{\"key\":\"ide_labs.link_clicked_count_changed_file_analysis_doc\",\"value\":\"10\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"ide_labs.link_clicked_count_privacy_policy\",\"value\":\"20\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"ide_labs.feedback_link_clicked_count_connected_mode\",\"value\":\"1\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"ide_labs.feedback_link_clicked_count_manage_dependency_risk\",\"value\":\"2\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"ai_hooks.windsurf_installed\",\"value\":\"2\",\"type\":\"integer\",\"granularity\":\"daily\"}," +
      "{\"key\":\"ai_hooks.cursor_installed\",\"value\":\"5\",\"type\":\"integer\",\"granularity\":\"daily\"}" +
      "]}");

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

    values.add(new TelemetryMeasuresValue("new_bindings.manual", String.valueOf(1), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("new_bindings.accepted_suggestion_remote_url", String.valueOf(2), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("new_bindings.accepted_suggestion_properties_file", String.valueOf(3), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("new_bindings.accepted_suggestion_shared_config_file", String.valueOf(4), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("new_bindings.accepted_suggestion_project_name", String.valueOf(5), INTEGER, DAILY));

    values.add(new TelemetryMeasuresValue("binding_suggestion_clue.remote_url", String.valueOf(5), INTEGER, DAILY));

    values.add(new TelemetryMeasuresValue("bindings.child_count", String.valueOf(1), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("bindings.server_count", String.valueOf(2), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("bindings.cloud_eu_count", String.valueOf(0), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("bindings.cloud_us_count", String.valueOf(0), INTEGER, DAILY));

    values.add(new TelemetryMeasuresValue("connections.attributes", new Gson().toJson(List.of(new TelemetryConnectionAttributes("user-id", null, "org-id"))), STRING, DAILY));

    values.add(new TelemetryMeasuresValue("help_and_feedback.doc_link", String.valueOf(5), INTEGER, DAILY));

    values.add(new TelemetryMeasuresValue("analysis_reporting.trigger_count_vcs_changed_files", String.valueOf(7), INTEGER, DAILY));

    values.add(new TelemetryMeasuresValue("performance.biggest_size_config_scope_files", String.valueOf(12345), INTEGER, DAILY));

    values.add(new TelemetryMeasuresValue("automatic_analysis.enabled", String.valueOf(true), BOOLEAN, DAILY));
    values.add(new TelemetryMeasuresValue("automatic_analysis.toggled_count", String.valueOf(1), INTEGER, DAILY));

    values.add(new TelemetryMeasuresValue("mcp.configuration_requested", String.valueOf(3), INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("mcp.integration_enabled", String.valueOf(true), BOOLEAN, DAILY));
    values.add(new TelemetryMeasuresValue("mcp.transport_mode", McpTransportMode.HTTP.name(), STRING, DAILY));

    values.add(new TelemetryMeasuresValue("ide_labs.joined", "true", BOOLEAN, DAILY));
    values.add(new TelemetryMeasuresValue("ide_labs.enabled", "true", BOOLEAN, DAILY));
    values.add(new TelemetryMeasuresValue("ide_labs.link_clicked_count_changed_file_analysis_doc", "10", INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("ide_labs.link_clicked_count_privacy_policy", "20", INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("ide_labs.feedback_link_clicked_count_connected_mode", "1", INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("ide_labs.feedback_link_clicked_count_manage_dependency_risk", "2", INTEGER, DAILY));

    values.add(new TelemetryMeasuresValue("ai_hooks.windsurf_installed", "2", INTEGER, DAILY));
    values.add(new TelemetryMeasuresValue("ai_hooks.cursor_installed", "5", INTEGER, DAILY));

    return values;
  }

  private static void assertValues(List<TelemetryMeasuresValue> values) {
    assertThat(values).extracting("key", "value", "type", "granularity")
      .contains(tuple("shared_connected_mode.manual", "1", INTEGER, DAILY))
      .contains(tuple("shared_connected_mode.imported", "2", INTEGER, DAILY))
      .contains(tuple("shared_connected_mode.auto", "3", INTEGER, DAILY))
      .contains(tuple("shared_connected_mode.exported", "4", INTEGER, DAILY))
      .contains(tuple("new_bindings.manual", "1", INTEGER, DAILY))
      .contains(tuple("new_bindings.accepted_suggestion_remote_url", "2", INTEGER, DAILY))
      .contains(tuple("new_bindings.accepted_suggestion_properties_file", "3", INTEGER, DAILY))
      .contains(tuple("new_bindings.accepted_suggestion_shared_config_file", "4", INTEGER, DAILY))
      .contains(tuple("new_bindings.accepted_suggestion_project_name", "5", INTEGER, DAILY))
      .contains(tuple("binding_suggestion_clue.remote_url", "5", INTEGER, DAILY))
      .contains(tuple("connections.attributes", "[{\"userId\":\"user-id\",\"organizationId\":\"org-id\"}]", STRING, DAILY))
      .contains(tuple("help_and_feedback.doc_link", "5", INTEGER, DAILY))
      .contains(tuple("analysis_reporting.trigger_count_vcs_changed_files", "7", INTEGER, DAILY))
      .contains(tuple("automatic_analysis.enabled", "true", BOOLEAN, DAILY))
      .contains(tuple("automatic_analysis.toggled_count", "1", INTEGER, DAILY))
      .contains(tuple("mcp.integration_enabled", "true", BOOLEAN, DAILY))
      .contains(tuple("mcp.transport_mode", "HTTP", STRING, DAILY))
      .contains(tuple("ide_labs.joined", "true", BOOLEAN, DAILY))
      .contains(tuple("ide_labs.enabled", "true", BOOLEAN, DAILY))
      .contains(tuple("ide_labs.link_clicked_count_changed_file_analysis_doc", "10", INTEGER, DAILY))
      .contains(tuple("ide_labs.link_clicked_count_privacy_policy", "20", INTEGER, DAILY))
      .contains(tuple("ide_labs.feedback_link_clicked_count_connected_mode", "1", INTEGER, DAILY))
      .contains(tuple("ide_labs.feedback_link_clicked_count_manage_dependency_risk", "2", INTEGER, DAILY))
      .contains(tuple("ai_hooks.windsurf_installed", "2", INTEGER, DAILY))
      .contains(tuple("ai_hooks.cursor_installed", "5", INTEGER, DAILY));
  }

}
