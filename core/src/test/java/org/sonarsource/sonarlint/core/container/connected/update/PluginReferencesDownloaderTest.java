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
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache.Downloader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class PluginReferencesDownloaderTest {
  private static final String PLUGIN_INDEX = "scmsvn,sonar-scm-svn-plugin-1.3-SNAPSHOT.jar|d0a68d150314d96d3469e0f2246f3537\n" +
    "javascript,sonar-javascript-plugin-2.10.jar|79dba9cab72d8d31767f47c03d169598\n" +
    "csharp,sonar-csharp-plugin-4.4.jar|e78bc8ac2e376c4a7a2a2cae914bdc52\n" +
    "groovy,sonar-groovy-plugin-1.2.jar|14908dd5f3a9b9d795dbc103f0af546f\n" +
    "java,sonar-java-plugin-3.12-SNAPSHOT.jar|de5308f43260d357acc97712ce4c5475";

  private PluginCache pluginCache;
  private PluginVersionChecker pluginVersionChecker;
  private Path dest;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void setUp() throws IOException {
    pluginCache = mock(PluginCache.class);
    pluginVersionChecker = mock(PluginVersionChecker.class);
    dest = temp.newFolder().toPath();
  }

  @Test
  public void update_all_plugins_before_6_0() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("deploy/plugins/index.txt", PLUGIN_INDEX);

    PluginReferencesDownloader pluginUpdate = new PluginReferencesDownloader(wsClient, pluginCache, pluginVersionChecker);

    pluginUpdate.fetchPluginsTo(dest, "5.6");
    PluginReferences pluginReferences = ProtobufUtil.readFile(dest.resolve(StorageManager.PLUGIN_REFERENCES_PB), PluginReferences.parser());
    assertThat(pluginReferences.getReferenceList()).extracting("key", "hash", "filename")
      .containsOnly(
        tuple("scmsvn", "d0a68d150314d96d3469e0f2246f3537", "sonar-scm-svn-plugin-1.3-SNAPSHOT.jar"),
        tuple("javascript", "79dba9cab72d8d31767f47c03d169598", "sonar-javascript-plugin-2.10.jar"),
        tuple("csharp", "e78bc8ac2e376c4a7a2a2cae914bdc52", "sonar-csharp-plugin-4.4.jar"),
        tuple("groovy", "14908dd5f3a9b9d795dbc103f0af546f", "sonar-groovy-plugin-1.2.jar"),
        tuple("java", "de5308f43260d357acc97712ce4c5475", "sonar-java-plugin-3.12-SNAPSHOT.jar"));

    verify(pluginCache).get(eq("sonar-java-plugin-3.12-SNAPSHOT.jar"), eq("de5308f43260d357acc97712ce4c5475"), any(Downloader.class));
    verify(pluginVersionChecker).checkPlugins(PLUGIN_INDEX);
  }

  @Test
  public void update_compatible_plugins_on_6_0() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("deploy/plugins/index.txt",
      "scmsvn,false,sonar-scm-svn-plugin-1.3-SNAPSHOT.jar|d0a68d150314d96d3469e0f2246f3537\n" +
        "javascript,false,sonar-javascript-plugin-2.10.jar|79dba9cab72d8d31767f47c03d169598\n" +
        "csharp,false,sonar-csharp-plugin-4.4.jar|e78bc8ac2e376c4a7a2a2cae914bdc52\n" +
        "groovy,true,sonar-groovy-plugin-1.2.jar|14908dd5f3a9b9d795dbc103f0af546f\n" +
        "java,true,sonar-java-plugin-3.12-SNAPSHOT.jar|de5308f43260d357acc97712ce4c5475");

    PluginReferencesDownloader pluginUpdate = new PluginReferencesDownloader(wsClient, pluginCache, pluginVersionChecker);

    pluginUpdate.fetchPluginsTo(dest, "6.0-SNAPSHOT");

    PluginReferences pluginReferences = ProtobufUtil.readFile(dest.resolve(StorageManager.PLUGIN_REFERENCES_PB), PluginReferences.parser());
    assertThat(pluginReferences.getReferenceList()).extracting("key", "hash", "filename")
      .containsOnly(tuple("java", "de5308f43260d357acc97712ce4c5475", "sonar-java-plugin-3.12-SNAPSHOT.jar"),
        tuple("groovy", "14908dd5f3a9b9d795dbc103f0af546f", "sonar-groovy-plugin-1.2.jar"),
        tuple("javascript", "79dba9cab72d8d31767f47c03d169598", "sonar-javascript-plugin-2.10.jar"));

    verify(pluginCache).get(eq("sonar-java-plugin-3.12-SNAPSHOT.jar"), eq("de5308f43260d357acc97712ce4c5475"), any(Downloader.class));
  }

}
