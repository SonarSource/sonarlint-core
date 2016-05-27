/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class GlobalPropertiesDownloaderTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Path destDir;

  @Before
  public void setUp() throws IOException {
    destDir = temp.newFolder().toPath();
  }

  @Test
  public void testFetchGlobalPropsDefaultPluginWhitelist() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithReaderResponse("/api/properties?format=json",
      new StringReader("[{\"key\": \"sonar.core.treemap.colormetric\",\"value\": \"violations_density\"},"
        + "{\"key\": \"sonar.core.treemap.sizemetric\",\"value\": \"ncloc\"},"
        + "{\"key\": \"views.servers\",\"value\": \"135817900907501\",\"values\": [\"135817900907501\"]}]"));

    Set<String> pluginKeys = new GlobalPropertiesDownloader(wsClient).fetchGlobalPropertiesTo(destDir, "5.6");
    assertThat(pluginKeys).containsOnly("java", "javascript", "php", "python", "cobol", "abap", "plsql", "swift");

    GlobalProperties properties = ProtobufUtil.readFile(destDir.resolve(StorageManager.PROPERTIES_PB), GlobalProperties.parser());
    assertThat(properties.getProperties()).containsOnly(entry("sonar.core.treemap.colormetric", "violations_density"),
      entry("sonar.core.treemap.sizemetric", "ncloc"),
      entry("views.servers", "135817900907501"));
  }

  @Test
  public void testFetchGlobalPropsPluginWhitelist() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithReaderResponse("/api/properties?format=json",
      new StringReader("[{\"key\": \"sonar.core.treemap.colormetric\",\"value\": \"violations_density\"},"
        + "{\"key\": \"sonarlint.plugins.whitelist\",\"value\": \"java\"}]"));

    Set<String> pluginKeys = new GlobalPropertiesDownloader(wsClient).fetchGlobalPropertiesTo(destDir, "5.6");
    assertThat(pluginKeys).containsOnly("java");
  }

  @Test
  public void testBatchGlobal() {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithReaderResponse("/api/properties?format=json",
      new StringReader("[{\"key\": \"sonar.core.treemap.colormetric\",\"value\": \"violations_density\"}]"));
    WsClientTestUtils.addResponse(wsClient, "/batch/global",
      "{ timestamp: 0, globalSettings: {"
        + "\"sonar.core.treemap.colormetric\":\"override\","
        + "\"sonar.core.treemap.sizemetric\":\"ncloc\""
        + "} }");
    new GlobalPropertiesDownloader(wsClient).fetchGlobalPropertiesTo(destDir, "5.5");
    GlobalProperties properties = ProtobufUtil.readFile(destDir.resolve(StorageManager.PROPERTIES_PB), GlobalProperties.parser());
    assertThat(properties.getProperties()).containsOnly(entry("sonar.core.treemap.colormetric", "violations_density"),
      entry("sonar.core.treemap.sizemetric", "ncloc"));
  }

  @Test(expected = IllegalStateException.class)
  public void invalidResponse() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithReaderResponse("/api/properties?format=json", new StringReader("foo bar"));

    new GlobalPropertiesDownloader(wsClient).fetchGlobalPropertiesTo(destDir, "5.6");
  }

}
