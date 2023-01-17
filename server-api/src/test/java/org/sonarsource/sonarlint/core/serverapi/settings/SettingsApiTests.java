/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class SettingsApiTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  @Test
  void testFetchProjectSettings() {
    var valuesBuilder = Settings.FieldValues.Value.newBuilder();
    valuesBuilder.putValue("filepattern", "**/*.xml");
    valuesBuilder.putValue("rulepattern", "*:S12345");
    var value1 = valuesBuilder.build();
    valuesBuilder.clear();
    valuesBuilder.putValue("filepattern", "**/*.java");
    valuesBuilder.putValue("rulepattern", "*:S456");
    var value2 = valuesBuilder.build();

    var response = Settings.ValuesWsResponse.newBuilder()
      .addSettings(Settings.Setting.newBuilder()
        .setKey("sonar.inclusions")
        .setValues(Settings.Values.newBuilder().addValues("**/*.java")))
      .addSettings(Settings.Setting.newBuilder()
        .setKey("sonar.java.fileSuffixes")
        .setValue("*.java"))
      .addSettings(Settings.Setting.newBuilder()
        .setKey("sonar.issue.exclusions.multicriteria")
        .setFieldValues(Settings.FieldValues.newBuilder().addFieldValues(value1).addFieldValues(value2)).build())
      .build();
    mockServer.addProtobufResponse("/api/settings/values.protobuf?component=foo", response);

    var projectSettings = new SettingsApi(mockServer.serverApiHelper()).getProjectSettings("foo");

    assertThat(projectSettings).containsOnly(
      entry("sonar.inclusions", "**/*.java"),
      entry("sonar.java.fileSuffixes", "*.java"),
      entry("sonar.issue.exclusions.multicriteria", "1,2"),
      entry("sonar.issue.exclusions.multicriteria.1.filepattern", "**/*.xml"),
      entry("sonar.issue.exclusions.multicriteria.1.rulepattern", "*:S12345"),
      entry("sonar.issue.exclusions.multicriteria.2.filepattern", "**/*.java"),
      entry("sonar.issue.exclusions.multicriteria.2.rulepattern", "*:S456"));
  }

}
