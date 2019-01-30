/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.plugin.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

  private static final String RESPONSE_67 = "{\"plugins\": [\n" +
    "    {\n" +
    "      \"key\": \"branch\",\n" +
    "      \"filename\": \"sonar-branch-plugin-1.1.0.879.jar\",\n" +
    "      \"sonarLintSupported\": false,\n" +
    "      \"hash\": \"064d334d27aa14aab6e39315428ee3cf\",\n" +
    "      \"version\": \"1.1 (build 879)\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"key\": \"javascript\",\n" +
    "      \"filename\": \"sonar-javascript-plugin-3.4.0.5828.jar\",\n" +
    "      \"sonarLintSupported\": true,\n" +
    "      \"hash\": \"d136fdb31fe38c3d780650f7228a49fa\",\n" +
    "      \"version\": \"3.4 (build 5828)\"\n" +
    "    } ]}";

  private SonarLintWsClient wsClient;
  private PluginVersionChecker pluginVersionChecker = mock(PluginVersionChecker.class);
  private ConnectedGlobalConfiguration globalConfig = mock(ConnectedGlobalConfiguration.class);

  @Before
  public void setUp() {
    when(pluginVersionChecker.getMinimumVersion(anyString())).thenReturn("1.0");
    when(pluginVersionChecker.isVersionSupported(anyString(), anyString())).thenReturn(true);
    when(pluginVersionChecker.isVersionSupported(eq("javascript"), anyString())).thenReturn(false);
    when(globalConfig.getExcludedCodeAnalyzers()).thenReturn(Collections.singleton("groovy"));
  }

  @Test
  public void testParsing() {
    wsClient = WsClientTestUtils.createMockWithResponse("/deploy/plugins/index.txt", PLUGIN_INDEX);

    List<SonarAnalyzer> pluginList = new PluginListDownloader(globalConfig, wsClient, pluginVersionChecker).downloadPluginList(Version.create("5.0"));
    assertThat(pluginList)
      .extracting(SonarAnalyzer::key, SonarAnalyzer::filename, SonarAnalyzer::hash, SonarAnalyzer::version, SonarAnalyzer::sonarlintCompatible, SonarAnalyzer::versionSupported)
      .containsOnly(
        tuple("scmsvn", "sonar-scm-svn-plugin-1.3-SNAPSHOT.jar", "d0a68d150314d96d3469e0f2246f3537", "1.3", true, true),
        tuple("javascript", "sonar-javascript-plugin-2.10.jar", "79dba9cab72d8d31767f47c03d169598", "2.10", true, false),
        tuple("csharp", "sonar-csharp-plugin-4.4.jar", "e78bc8ac2e376c4a7a2a2cae914bdc52", "4.4", true, true),
        tuple("groovy", "sonar-groovy-plugin-1.2.jar", "14908dd5f3a9b9d795dbc103f0af546f", "1.2", false, true),
        tuple("java", "sonar-java-plugin-3.12-SNAPSHOT.jar", "de5308f43260d357acc97712ce4c5475", "3.12", true, true));
  }

  @Test
  public void testParsing60() {
    wsClient = WsClientTestUtils.createMockWithResponse("/deploy/plugins/index.txt", PLUGIN_INDEX60);

    List<SonarAnalyzer> pluginList = new PluginListDownloader(globalConfig, wsClient, pluginVersionChecker).downloadPluginList(Version.create("6.0"));
    assertThat(pluginList)
      .extracting(SonarAnalyzer::key, SonarAnalyzer::filename, SonarAnalyzer::hash, SonarAnalyzer::version, SonarAnalyzer::sonarlintCompatible, SonarAnalyzer::versionSupported)
      .containsOnly(
        tuple("scmsvn", "sonar-scm-svn-plugin-1.3-SNAPSHOT.jar", "d0a68d150314d96d3469e0f2246f3537", "1.3", true, true),
        tuple("javascript", "sonar-javascript-plugin-2.10.jar", "79dba9cab72d8d31767f47c03d169598", "2.10", true, false),
        tuple("csharp", "sonar-csharp-plugin-4.4.jar", "e78bc8ac2e376c4a7a2a2cae914bdc52", "4.4", true, true),
        tuple("groovy", "sonar-groovy-plugin-1.2.jar", "14908dd5f3a9b9d795dbc103f0af546f", "1.2", false, true),
        tuple("java", "sonar-java-plugin-3.12-SNAPSHOT.jar", "de5308f43260d357acc97712ce4c5475", "3.12", false, true));
  }

  @Test
  public void testParsing67() {
    wsClient = WsClientTestUtils.createMockWithResponse("/api/plugins/installed", RESPONSE_67);
    List<SonarAnalyzer> pluginList = new PluginListDownloader(globalConfig, wsClient, pluginVersionChecker).downloadPluginList(Version.create("6.7"));
    assertThat(pluginList)
      .extracting(SonarAnalyzer::key, SonarAnalyzer::filename, SonarAnalyzer::hash, SonarAnalyzer::version, SonarAnalyzer::sonarlintCompatible, SonarAnalyzer::versionSupported)
      .containsOnly(
        tuple("branch", "sonar-branch-plugin-1.1.0.879.jar", "064d334d27aa14aab6e39315428ee3cf", "1.1.0.879", false, true),
        tuple("javascript", "sonar-javascript-plugin-3.4.0.5828.jar", "d136fdb31fe38c3d780650f7228a49fa", "3.4.0.5828", true, false));
  }
}
