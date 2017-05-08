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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.model.DefaultSonarAnalyzer;

public class PluginListDownloaderTest {
  private static final String PLUGIN_INDEX = "scmsvn,sonar-scm-svn-plugin-1.3-SNAPSHOT.jar|d0a68d150314d96d3469e0f2246f3537\n" +
    "javascript,sonar-javascript-plugin-2.10.jar|79dba9cab72d8d31767f47c03d169598\n" +
    "csharp,sonar-csharp-plugin-4.4.jar|e78bc8ac2e376c4a7a2a2cae914bdc52\n" +
    "groovy,sonar-groovy-plugin-1.2.jar|14908dd5f3a9b9d795dbc103f0af546f\n" +
    "java,sonar-java-plugin-3.12-SNAPSHOT.jar|de5308f43260d357acc97712ce4c5475";

  private static final String PLUGIN_INDEX60 = "scmsvn,true,sonar-scm-svn-plugin-1.3-SNAPSHOT.jar|d0a68d150314d96d3469e0f2246f3537\n" +
    "javascript,true,sonar-javascript-plugin-2.10.jar|79dba9cab72d8d31767f47c03d169598\n" +
    "csharp,true,sonar-csharp-plugin-4.4.jar|e78bc8ac2e376c4a7a2a2cae914bdc52\n" +
    "groovy,true,sonar-groovy-plugin-1.2.jar|14908dd5f3a9b9d795dbc103f0af546f\n" +
    "java,false,sonar-java-plugin-3.12-SNAPSHOT.jar|de5308f43260d357acc97712ce4c5475";

  private SonarLintWsClient wsClient;
  private PluginVersionChecker pluginVersionChecker;

  @Before
  public void setUp() {
    pluginVersionChecker = mock(PluginVersionChecker.class);
    when(pluginVersionChecker.getMinimumVersion(anyString())).thenReturn("0.0");
  }

  @Test
  public void testParsing() {
    wsClient = WsClientTestUtils.createMockWithResponse("/deploy/plugins/index.txt", PLUGIN_INDEX);

    List<SonarAnalyzer> pluginList = new PluginListDownloader(wsClient, pluginVersionChecker).downloadPluginList("5.0");
    // compatibility will depend on the white list
    assertThat(pluginList).extracting("key", "filename", "hash", "version", "sonarlintCompatible").containsOnly(
      tuple("scmsvn", "sonar-scm-svn-plugin-1.3-SNAPSHOT.jar", "d0a68d150314d96d3469e0f2246f3537", "1.3", false),
      tuple("javascript", "sonar-javascript-plugin-2.10.jar", "79dba9cab72d8d31767f47c03d169598", "2.10", true),
      tuple("csharp", "sonar-csharp-plugin-4.4.jar", "e78bc8ac2e376c4a7a2a2cae914bdc52", "4.4", false),
      tuple("groovy", "sonar-groovy-plugin-1.2.jar", "14908dd5f3a9b9d795dbc103f0af546f", "1.2", false),
      tuple("java", "sonar-java-plugin-3.12-SNAPSHOT.jar", "de5308f43260d357acc97712ce4c5475", "3.12", true));
  }

  @Test
  public void testParsing60() {
    wsClient = WsClientTestUtils.createMockWithResponse("/deploy/plugins/index.txt", PLUGIN_INDEX60);

    List<SonarAnalyzer> pluginList = new PluginListDownloader(wsClient, pluginVersionChecker).downloadPluginList("6.0");
    assertThat(pluginList).extracting("key", "filename", "hash", "version", "sonarlintCompatible").containsOnly(
      tuple("scmsvn", "sonar-scm-svn-plugin-1.3-SNAPSHOT.jar", "d0a68d150314d96d3469e0f2246f3537", "1.3", true),
      tuple("javascript", "sonar-javascript-plugin-2.10.jar", "79dba9cab72d8d31767f47c03d169598", "2.10", true),
      tuple("csharp", "sonar-csharp-plugin-4.4.jar", "e78bc8ac2e376c4a7a2a2cae914bdc52", "4.4", true),
      tuple("groovy", "sonar-groovy-plugin-1.2.jar", "14908dd5f3a9b9d795dbc103f0af546f", "1.2", true),
      // java has flag=false, but is whitelisted
      tuple("java", "sonar-java-plugin-3.12-SNAPSHOT.jar", "de5308f43260d357acc97712ce4c5475", "3.12", true));
  }
}
