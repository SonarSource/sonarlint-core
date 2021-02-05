/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.Settings;
import org.sonarqube.ws.Settings.FieldValues;
import org.sonarqube.ws.Settings.FieldValues.Value;
import org.sonarqube.ws.Settings.Setting;
import org.sonarqube.ws.Settings.Values;
import org.sonarqube.ws.Settings.ValuesWsResponse;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration.Builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsDownloaderTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private SettingsDownloader underTest;

  @BeforeEach
  public void setUp() {
    underTest = new SettingsDownloader(mockServer.serverApiHelper());
  }

  @Test
  void testFetchGlobalSettings(@TempDir Path tempDir) throws Exception {
    ValuesWsResponse response = ValuesWsResponse.newBuilder()
      .addSettings(Setting.newBuilder()
        .setKey("sonar.core.treemap.colormetric")
        .setValue("violations_density")
        .setInherited(true))
      .addSettings(Setting.newBuilder()
        .setKey("sonar.core.treemap.sizemetric")
        .setValue("ncloc"))
      .addSettings(Setting.newBuilder()
        .setKey("views.servers")
        .setValues(Values.newBuilder().addValues("135817900907501")))
      .build();
    mockServer.addProtobufResponse("/api/settings/values.protobuf", response);

    underTest.fetchGlobalSettingsTo(tempDir);

    GlobalProperties properties = ProtobufUtil.readFile(tempDir.resolve(StoragePaths.PROPERTIES_PB), GlobalProperties.parser());
    assertThat(properties.getPropertiesMap()).containsOnly(
      entry("sonar.core.treemap.sizemetric", "ncloc"),
      entry("views.servers", "135817900907501"));
  }

  @Test
  void testFetchProjectSettings() throws Exception {

    Settings.FieldValues.Value.Builder valuesBuilder = Value.newBuilder();
    valuesBuilder.putValue("filepattern", "**/*.xml");
    valuesBuilder.putValue("rulepattern", "*:S12345");
    Value value1 = valuesBuilder.build();
    valuesBuilder.clear();
    valuesBuilder.putValue("filepattern", "**/*.java");
    valuesBuilder.putValue("rulepattern", "*:S456");
    Value value2 = valuesBuilder.build();

    ValuesWsResponse response = ValuesWsResponse.newBuilder()
      .addSettings(Setting.newBuilder()
        .setKey("sonar.inclusions")
        .setValues(Values.newBuilder().addValues("**/*.java")))
      .addSettings(Setting.newBuilder()
        .setKey("sonar.java.fileSuffixes")
        .setValue("*.java"))
      .addSettings(Setting.newBuilder()
        .setKey("sonar.issue.exclusions.multicriteria")
        .setFieldValues(FieldValues.newBuilder().addFieldValues(value1).addFieldValues(value2)).build())
      .build();
    mockServer.addProtobufResponse("/api/settings/values.protobuf?component=foo", response);

    Builder builder = ProjectConfiguration.newBuilder();
    underTest.fetchProjectSettings("foo", null, builder);

    assertThat(builder.getPropertiesMap()).containsOnly(
      entry("sonar.inclusions", "**/*.java"),
      entry("sonar.java.fileSuffixes", "*.java"),
      entry("sonar.issue.exclusions.multicriteria", "1,2"),
      entry("sonar.issue.exclusions.multicriteria.1.filepattern", "**/*.xml"),
      entry("sonar.issue.exclusions.multicriteria.1.rulepattern", "*:S12345"),
      entry("sonar.issue.exclusions.multicriteria.2.filepattern", "**/*.java"),
      entry("sonar.issue.exclusions.multicriteria.2.rulepattern", "*:S456"));
  }

  @Test
  void invalidResponseSettings(@TempDir Path tempDir) {
    mockServer.addStringResponse("/api/settings/values.protobuf", "foo bar");

    assertThrows(IllegalStateException.class, () -> underTest.fetchGlobalSettingsTo(tempDir));
  }

}
