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
import org.sonarsource.sonarlint.core.client.api.connected.Language;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PluginListDownloaderTest {

  public static final String RESPONSE_67 = "{\"plugins\": [\n" +
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
    "    },\n" +
    "    {\n" +
    "      \"key\": \"java\",\n" +
    "      \"filename\": \"sonar-java-plugin-4.0.0.1234.jar\",\n" +
    "      \"sonarLintSupported\": true,\n" +
    "      \"hash\": \"foobar\",\n" +
    "      \"version\": \"4.0 (build 1234)\"\n" +
    "    } ]}";

  private SonarLintWsClient wsClient;
  private PluginVersionChecker pluginVersionChecker = mock(PluginVersionChecker.class);
  private ConnectedGlobalConfiguration globalConfig = mock(ConnectedGlobalConfiguration.class);

  @Before
  public void setUp() {
    when(pluginVersionChecker.getMinimumVersion(anyString())).thenReturn("1.0");
    when(pluginVersionChecker.isVersionSupported(anyString(), anyString())).thenReturn(true);
    when(pluginVersionChecker.isVersionSupported(eq("javascript"), anyString())).thenReturn(false);
    when(globalConfig.getEnabledLanguages()).thenReturn(Collections.singleton(Language.JS));
  }

  @Test
  public void testParsing() {
    wsClient = WsClientTestUtils.createMockWithResponse("/api/plugins/installed", RESPONSE_67);
    List<SonarAnalyzer> pluginList = new PluginListDownloader(globalConfig, wsClient, pluginVersionChecker).downloadPluginList();
    assertThat(pluginList)
      .extracting(SonarAnalyzer::key, SonarAnalyzer::filename, SonarAnalyzer::hash, SonarAnalyzer::version, SonarAnalyzer::sonarlintCompatible, SonarAnalyzer::versionSupported)
      .containsOnly(
        // Don't have the sonarlint-supported flag
        tuple("branch", "sonar-branch-plugin-1.1.0.879.jar", "064d334d27aa14aab6e39315428ee3cf", "1.1.0.879", false, true),
        // Not part of enabled languages
        tuple("java", "sonar-java-plugin-4.0.0.1234.jar", "foobar", "4.0.0.1234", false, true),
        // Enabled language
        tuple("javascript", "sonar-javascript-plugin-3.4.0.5828.jar", "d136fdb31fe38c3d780650f7228a49fa", "3.4.0.5828", true, false));
  }
}
