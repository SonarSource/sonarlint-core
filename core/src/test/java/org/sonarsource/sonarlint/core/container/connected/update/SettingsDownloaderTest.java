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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.StringReader;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.Settings;
import org.sonarqube.ws.Settings.FieldValues;
import org.sonarqube.ws.Settings.FieldValues.Value;
import org.sonarqube.ws.Settings.Setting;
import org.sonarqube.ws.Settings.Values;
import org.sonarqube.ws.Settings.ValuesWsResponse;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration.Builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class SettingsDownloaderTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Path destDir;

  @Before
  public void setUp() throws IOException {
    destDir = temp.newFolder().toPath();
  }

  @Test
  public void testFetchGlobalSettings() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
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
    PipedInputStream in = new PipedInputStream();
    final PipedOutputStream out = new PipedOutputStream(in);
    response.writeTo(out);
    out.close();
    WsClientTestUtils.addResponse(wsClient, "/api/settings/values.protobuf", in);

    new SettingsDownloader(wsClient).fetchGlobalSettingsTo("6.3", destDir);

    GlobalProperties properties = ProtobufUtil.readFile(destDir.resolve(StoragePaths.PROPERTIES_PB), GlobalProperties.parser());
    assertThat(properties.getPropertiesMap()).containsOnly(
      entry("sonar.core.treemap.sizemetric", "ncloc"),
      entry("views.servers", "135817900907501"));
  }

  @Test
  public void testFetchProjectSettings() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();

    Settings.FieldValues.Value.Builder valuesBuilder = Value.newBuilder();
    valuesBuilder.getMutableValue().put("filepattern", "**/*.xml");
    valuesBuilder.getMutableValue().put("rulepattern", "*:S12345");
    Value value1 = valuesBuilder.build();
    valuesBuilder.clear();
    valuesBuilder.getMutableValue().put("filepattern", "**/*.java");
    valuesBuilder.getMutableValue().put("rulepattern", "*:S456");
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
    PipedInputStream in = new PipedInputStream();
    final PipedOutputStream out = new PipedOutputStream(in);
    response.writeTo(out);
    out.close();
    WsClientTestUtils.addResponse(wsClient, "/api/settings/values.protobuf?component=foo", in);

    Builder builder = ModuleConfiguration.newBuilder();
    new SettingsDownloader(wsClient).fetchProjectSettings("6.3", "foo", null, builder);

    assertThat(builder.getPropertiesMap()).containsOnly(
      entry("sonar.inclusions", "**/*.java"),
      entry("sonar.java.fileSuffixes", "*.java"),
      entry("sonar.issue.exclusions.multicriteria", "1,2"),
      entry("sonar.issue.exclusions.multicriteria.1.filepattern", "**/*.xml"),
      entry("sonar.issue.exclusions.multicriteria.1.rulepattern", "*:S12345"),
      entry("sonar.issue.exclusions.multicriteria.2.filepattern", "**/*.java"),
      entry("sonar.issue.exclusions.multicriteria.2.rulepattern", "*:S456"));
  }

  @Test(expected = IllegalStateException.class)
  public void invalidResponseSettings() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    WsClientTestUtils.addResponse(wsClient, "/api/settings/values.protobuf", new ByteArrayInputStream("foo bar".getBytes()));

    new SettingsDownloader(wsClient).fetchGlobalSettingsTo("6.3", destDir);
  }

  @Test
  public void testFetchGlobalProperties() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithReaderResponse("/api/properties?format=json",
      new StringReader("[{\"key\": \"sonar.core.treemap.colormetric\",\"value\": \"violations_density\"},"
        + "{\"key\": \"sonar.core.treemap.sizemetric\",\"value\": \"ncloc\"},"
        + "{\"key\": \"views.servers\",\"value\": \"135817900907501\",\"values\": [\"135817900907501\"]}]"));

    new SettingsDownloader(wsClient).fetchGlobalSettingsTo("6.2", destDir);

    GlobalProperties properties = ProtobufUtil.readFile(destDir.resolve(StoragePaths.PROPERTIES_PB), GlobalProperties.parser());
    assertThat(properties.getPropertiesMap()).containsOnly(entry("sonar.core.treemap.colormetric", "violations_density"),
      entry("sonar.core.treemap.sizemetric", "ncloc"),
      entry("views.servers", "135817900907501"));
  }

  @Test
  public void testFetchProjectProperties() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithReaderResponse("/api/properties?format=json&resource=foo",
      new StringReader("[{\"key\": \"sonar.inclusions\",\"value\": \"**/*.java\"},"
        + "{\"key\": \"sonar.java.fileSuffixes\",\"value\": \"*.java\"}]"));

    Builder builder = ModuleConfiguration.newBuilder();
    new SettingsDownloader(wsClient).fetchProjectSettings("6.2", "foo", GlobalProperties.newBuilder().build(), builder);

    assertThat(builder.getPropertiesMap()).containsOnly(entry("sonar.inclusions", "**/*.java"),
      entry("sonar.java.fileSuffixes", "*.java"));
  }

  @Test
  public void testFetchProjectProperties_exclude_global_props() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithReaderResponse("/api/properties?format=json&resource=foo",
      new StringReader("[{\"key\": \"sonar.inclusions\",\"value\": \"**/*.java\"},"
        + "{\"key\": \"sonar.java.fileSuffixes\",\"value\": \"*.java\"}]"));

    Builder builder = ModuleConfiguration.newBuilder();
    new SettingsDownloader(wsClient).fetchProjectSettings("6.2", "foo", GlobalProperties.newBuilder().putProperties("sonar.inclusions", "**/*.java").build(), builder);

    assertThat(builder.getPropertiesMap()).containsOnly(entry("sonar.java.fileSuffixes", "*.java"));
  }

  @Test(expected = IllegalStateException.class)
  public void invalidResponseProperties() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithReaderResponse("/api/properties?format=json", new StringReader("foo bar"));

    new SettingsDownloader(wsClient).fetchGlobalSettingsTo("6.2", destDir);
  }

}
