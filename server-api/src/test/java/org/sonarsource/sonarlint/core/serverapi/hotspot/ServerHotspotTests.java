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
package org.sonarsource.sonarlint.core.serverapi.hotspot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerHotspotTests {
  @Test
  void it_should_populate_fields_with_constructor_parameters() {
    var hotspot = new ServerHotspot("message",
      "path",
      new ServerHotspot.TextRange(0, 1, 2, 3),
      "author",
      ServerHotspot.Status.TO_REVIEW,
      ServerHotspot.Resolution.FIXED, new ServerHotspot.Rule(
        "key",
        "name",
        "category",
        ServerHotspot.Rule.Probability.HIGH,
        "risk",
        "vulnerability",
        "fix"),
      "some code \n content");

    assertThat(hotspot.message).isEqualTo("message");
    assertThat(hotspot.filePath).isEqualTo("path");
    assertThat(hotspot.textRange.getStartLine()).isZero();
    assertThat(hotspot.textRange.getStartLineOffset()).isEqualTo(1);
    assertThat(hotspot.textRange.getEndLine()).isEqualTo(2);
    assertThat(hotspot.textRange.getEndLineOffset()).isEqualTo(3);
    assertThat(hotspot.author).isEqualTo("author");
    assertThat(hotspot.status).isEqualTo(ServerHotspot.Status.TO_REVIEW);
    assertThat(hotspot.resolution).isEqualTo(ServerHotspot.Resolution.FIXED);
    assertThat(hotspot.rule.key).isEqualTo("key");
    assertThat(hotspot.rule.name).isEqualTo("name");
    assertThat(hotspot.rule.securityCategory).isEqualTo("category");
    assertThat(hotspot.rule.vulnerabilityProbability).isEqualTo(ServerHotspot.Rule.Probability.HIGH);
    assertThat(hotspot.rule.riskDescription).isEqualTo("risk");
    assertThat(hotspot.rule.vulnerabilityDescription).isEqualTo("vulnerability");
    assertThat(hotspot.rule.fixRecommendations).isEqualTo("fix");
    assertThat(hotspot.codeSnippet).isEqualTo("some code \n content");

  }

}
