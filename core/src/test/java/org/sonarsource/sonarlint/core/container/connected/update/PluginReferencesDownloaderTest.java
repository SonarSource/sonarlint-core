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

import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
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

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void update_allowed_plugins() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("deploy/plugins/index.txt",
      "scmsvn,sonar-scm-svn-plugin-1.3-SNAPSHOT.jar|d0a68d150314d96d3469e0f2246f3537\n" +
        "javascript,sonar-javascript-plugin-2.10.jar|79dba9cab72d8d31767f47c03d169598\n" +
        "csharp,sonar-csharp-plugin-4.4.jar|e78bc8ac2e376c4a7a2a2cae914bdc52\n" +
        "groovy,sonar-groovy-plugin-1.2.jar|14908dd5f3a9b9d795dbc103f0af546f\n" +
        "java,sonar-java-plugin-3.12-SNAPSHOT.jar|de5308f43260d357acc97712ce4c5475");

    PluginCache pluginCache = mock(PluginCache.class);

    PluginReferencesDownloader pluginUpdate = new PluginReferencesDownloader(wsClient, pluginCache);

    Path dest = temp.newFolder().toPath();

    pluginUpdate.fetchPluginsTo(dest, ImmutableSet.of("java"));

    PluginReferences pluginReferences = ProtobufUtil.readFile(dest.resolve(StorageManager.PLUGIN_REFERENCES_PB), PluginReferences.parser());
    assertThat(pluginReferences.getReferenceList()).extracting("key", "hash", "filename")
      .containsOnly(tuple("java", "de5308f43260d357acc97712ce4c5475", "sonar-java-plugin-3.12-SNAPSHOT.jar"));

    verify(pluginCache).get(eq("sonar-java-plugin-3.12-SNAPSHOT.jar"), eq("de5308f43260d357acc97712ce4c5475"), any(Downloader.class));
  }

  @Test
  public void update_allowed_plugins_on_4_5() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("deploy/plugins/index.txt",
      "scmsvn,false,sonar-scm-svn-plugin-1.3-SNAPSHOT.jar|d0a68d150314d96d3469e0f2246f3537\n" +
        "javascript,false,sonar-javascript-plugin-2.10.jar|79dba9cab72d8d31767f47c03d169598\n" +
        "csharp,false,sonar-csharp-plugin-4.4.jar|e78bc8ac2e376c4a7a2a2cae914bdc52\n" +
        "groovy,false,sonar-groovy-plugin-1.2.jar|14908dd5f3a9b9d795dbc103f0af546f\n" +
        "java,false,sonar-java-plugin-3.12-SNAPSHOT.jar|de5308f43260d357acc97712ce4c5475");

    PluginCache pluginCache = mock(PluginCache.class);

    PluginReferencesDownloader pluginUpdate = new PluginReferencesDownloader(wsClient, pluginCache);

    Path dest = temp.newFolder().toPath();

    pluginUpdate.fetchPluginsTo(dest, ImmutableSet.of("java"));

    PluginReferences pluginReferences = ProtobufUtil.readFile(dest.resolve(StorageManager.PLUGIN_REFERENCES_PB), PluginReferences.parser());
    assertThat(pluginReferences.getReferenceList()).extracting("key", "hash", "filename")
      .containsOnly(tuple("java", "de5308f43260d357acc97712ce4c5475", "sonar-java-plugin-3.12-SNAPSHOT.jar"));

    verify(pluginCache).get(eq("sonar-java-plugin-3.12-SNAPSHOT.jar"), eq("de5308f43260d357acc97712ce4c5475"), any(Downloader.class));
  }

}
