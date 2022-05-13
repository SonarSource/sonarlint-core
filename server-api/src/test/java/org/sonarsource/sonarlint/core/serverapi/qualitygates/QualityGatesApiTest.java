/*
 * SonarLint Server API
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.qualitygates;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarqube.ws.Qualitygates;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class QualityGatesApiTest {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  @Test
  void should_get_by_project() {
    var underTest = new QualityGatesApi(mockServer.serverApiHelper());

    mockServer.addProtobufResponse("/api/qualitygates/get_by_project.protobuf?project=projectKey", Qualitygates.GetByProjectResponse.newBuilder()
      .setQualityGate(Qualitygates.QualityGate.newBuilder()
        .setId("id")
        .build())
      .build());

    var qualityGateId = underTest.getId("projectKey");

    assertThat(qualityGateId).isEqualTo("id");
  }

  @Test
  void should_show_quality_gate() {
    var underTest = new QualityGatesApi(mockServer.serverApiHelper());

    mockServer.addProtobufResponse("/api/qualitygates/show.protobuf?id=id", Qualitygates.ShowWsResponse.newBuilder()
      .addConditions(Qualitygates.ShowWsResponse.Condition.newBuilder()
        .setMetric("metric")
        .setOp("op")
        .setError("error")
        .build())
      .build());

    var qualityGate = underTest.getQualityGate("id");

    assertThat(qualityGate.getConditions())
      .extracting("metricKey", "operator", "threshold")
      .containsOnly(tuple("metric", "op", "error"));
  }
}
