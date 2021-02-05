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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.client.api.common.Version;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.container.model.DefaultSonarAnalyzer;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache.Copier;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PluginReferencesDownloaderTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private final PluginCache pluginCache = mock(PluginCache.class);
  private final List<SonarAnalyzer> pluginList = new LinkedList<>();
  private PluginReferencesDownloader underTest;

  @BeforeEach
  public void setUp() throws IOException {
    underTest = new PluginReferencesDownloader(mockServer.serverApiHelper(), pluginCache);
  }

  @Test
  void update_all_plugins_before_7_2(@TempDir Path tmp) throws Exception {
    Path dest = tmp.resolve("destDir");
    Files.createDirectory(dest);

    pluginList.add(new DefaultSonarAnalyzer("scmsvn", "sonar-scm-svn-plugin-1.3-SNAPSHOT.jar", "d0a68d150314d96d3469e0f2246f3537", "1.3-SNAPSHOT", true));
    pluginList.add(new DefaultSonarAnalyzer("javascript", "sonar-javascript-plugin-2.10.jar", "79dba9cab72d8d31767f47c03d169598", "2.10", true));
    pluginList.add(new DefaultSonarAnalyzer("csharp", "sonar-csharp-plugin-4.4.jar", "e78bc8ac2e376c4a7a2a2cae914bdc52", "4.4", true));
    pluginList.add(new DefaultSonarAnalyzer("groovy", "sonar-groovy-plugin-1.2.jar", "14908dd5f3a9b9d795dbc103f0af546f", "1.2", true));
    pluginList.add(new DefaultSonarAnalyzer("java", "sonar-java-plugin-3.12-SNAPSHOT.jar", "de5308f43260d357acc97712ce4c5475", "3.12-SNAPSHOT", true));

    underTest.fetchPluginsTo(Version.create("7.1"), dest, pluginList, new ProgressWrapper(null));

    PluginReferences pluginReferences = ProtobufUtil.readFile(dest.resolve(StoragePaths.PLUGIN_REFERENCES_PB), PluginReferences.parser());
    assertThat(pluginReferences.getReferenceList()).extracting("key", "hash", "filename")
      .containsOnly(
        tuple("scmsvn", "d0a68d150314d96d3469e0f2246f3537", "sonar-scm-svn-plugin-1.3-SNAPSHOT.jar"),
        tuple("javascript", "79dba9cab72d8d31767f47c03d169598", "sonar-javascript-plugin-2.10.jar"),
        tuple("csharp", "e78bc8ac2e376c4a7a2a2cae914bdc52", "sonar-csharp-plugin-4.4.jar"),
        tuple("groovy", "14908dd5f3a9b9d795dbc103f0af546f", "sonar-groovy-plugin-1.2.jar"),
        tuple("java", "de5308f43260d357acc97712ce4c5475", "sonar-java-plugin-3.12-SNAPSHOT.jar"));

    verify(pluginCache).get(eq("sonar-java-plugin-3.12-SNAPSHOT.jar"), eq("de5308f43260d357acc97712ce4c5475"), any(Copier.class));

    ArgumentCaptor<Copier> downloaderCaptor = ArgumentCaptor.forClass(Copier.class);
    verify(pluginCache).get(eq("sonar-java-plugin-3.12-SNAPSHOT.jar"), eq("de5308f43260d357acc97712ce4c5475"), downloaderCaptor.capture());
    Copier downloader = downloaderCaptor.getValue();
    mockServer.addStringResponse("/deploy/plugins/java/test.jar", "content");
    Path testFile = tmp.resolve("testFile");
    downloader.copy("test.jar", testFile);
    assertThat(testFile).hasContent("content");
  }

  @Test
  void update_all_plugins_from_7_2(@TempDir Path tmp) throws Exception {
    Path dest = tmp.resolve("destDir");
    Files.createDirectory(dest);

    pluginList.add(new DefaultSonarAnalyzer("scmsvn", "sonar-scm-svn-plugin-1.3-SNAPSHOT.jar", "d0a68d150314d96d3469e0f2246f3537", "1.3-SNAPSHOT", true));
    pluginList.add(new DefaultSonarAnalyzer("javascript", "sonar-javascript-plugin-2.10.jar", "79dba9cab72d8d31767f47c03d169598", "2.10", true));
    pluginList.add(new DefaultSonarAnalyzer("csharp", "sonar-csharp-plugin-4.4.jar", "e78bc8ac2e376c4a7a2a2cae914bdc52", "4.4", true));
    pluginList.add(new DefaultSonarAnalyzer("groovy", "sonar-groovy-plugin-1.2.jar", "14908dd5f3a9b9d795dbc103f0af546f", "1.2", true));
    pluginList.add(new DefaultSonarAnalyzer("java", "sonar-java-plugin-3.12-SNAPSHOT.jar", "de5308f43260d357acc97712ce4c5475", "3.12-SNAPSHOT", true));

    underTest.fetchPluginsTo(Version.create("7.2"), dest, pluginList, new ProgressWrapper(null));
    PluginReferences pluginReferences = ProtobufUtil.readFile(dest.resolve(StoragePaths.PLUGIN_REFERENCES_PB), PluginReferences.parser());
    assertThat(pluginReferences.getReferenceList()).extracting("key", "hash", "filename")
      .containsOnly(
        tuple("scmsvn", "d0a68d150314d96d3469e0f2246f3537", "sonar-scm-svn-plugin-1.3-SNAPSHOT.jar"),
        tuple("javascript", "79dba9cab72d8d31767f47c03d169598", "sonar-javascript-plugin-2.10.jar"),
        tuple("csharp", "e78bc8ac2e376c4a7a2a2cae914bdc52", "sonar-csharp-plugin-4.4.jar"),
        tuple("groovy", "14908dd5f3a9b9d795dbc103f0af546f", "sonar-groovy-plugin-1.2.jar"),
        tuple("java", "de5308f43260d357acc97712ce4c5475", "sonar-java-plugin-3.12-SNAPSHOT.jar"));

    verify(pluginCache).get(eq("sonar-java-plugin-3.12-SNAPSHOT.jar"), eq("de5308f43260d357acc97712ce4c5475"), any(Copier.class));

    ArgumentCaptor<Copier> downloaderCaptor = ArgumentCaptor.forClass(Copier.class);
    verify(pluginCache).get(eq("sonar-java-plugin-3.12-SNAPSHOT.jar"), eq("de5308f43260d357acc97712ce4c5475"), downloaderCaptor.capture());
    Copier downloader = downloaderCaptor.getValue();
    mockServer.addStringResponse("/api/plugins/download?plugin=java", "content");
    Path testFile = tmp.resolve("testFile");
    downloader.copy("test.jar", testFile);
    assertThat(testFile).hasContent("content");
  }

  @Test
  void filter_not_compatible(@TempDir Path dest) throws Exception {
    pluginList.add(new DefaultSonarAnalyzer("scmsvn", "sonar-scm-svn-plugin-1.3-SNAPSHOT.jar", "d0a68d150314d96d3469e0f2246f3537", "1.3-SNAPSHOT", false));
    pluginList.add(new DefaultSonarAnalyzer("javascript", "sonar-javascript-plugin-2.10.jar", "79dba9cab72d8d31767f47c03d169598", "2.10", true));
    pluginList.add(new DefaultSonarAnalyzer("csharp", "sonar-csharp-plugin-4.4.jar", "e78bc8ac2e376c4a7a2a2cae914bdc52", "4.4", false));
    pluginList.add(new DefaultSonarAnalyzer("groovy", "sonar-groovy-plugin-1.2.jar", "14908dd5f3a9b9d795dbc103f0af546f", "1.2", true));
    pluginList.add(new DefaultSonarAnalyzer("java", "sonar-java-plugin-3.12-SNAPSHOT.jar", "de5308f43260d357acc97712ce4c5475", "3.12-SNAPSHOT", true));

    underTest.fetchPluginsTo(Version.create("7.1"), dest, pluginList, new ProgressWrapper(null));

    PluginReferences pluginReferences = ProtobufUtil.readFile(dest.resolve(StoragePaths.PLUGIN_REFERENCES_PB), PluginReferences.parser());
    assertThat(pluginReferences.getReferenceList()).extracting("key", "hash", "filename")
      .containsOnly(tuple("java", "de5308f43260d357acc97712ce4c5475", "sonar-java-plugin-3.12-SNAPSHOT.jar"),
        tuple("groovy", "14908dd5f3a9b9d795dbc103f0af546f", "sonar-groovy-plugin-1.2.jar"),
        tuple("javascript", "79dba9cab72d8d31767f47c03d169598", "sonar-javascript-plugin-2.10.jar"));

    verify(pluginCache).get(eq("sonar-java-plugin-3.12-SNAPSHOT.jar"), eq("de5308f43260d357acc97712ce4c5475"), any(Copier.class));
  }

  @Test
  void filter_version_not_supported(@TempDir Path dest) throws Exception {
    pluginList.add(new DefaultSonarAnalyzer("scmsvn", "sonar-scm-svn-plugin-1.3-SNAPSHOT.jar", "d0a68d150314d96d3469e0f2246f3537", "1.3-SNAPSHOT", true)
      .versionSupported(false));
    pluginList.add(new DefaultSonarAnalyzer("javascript", "sonar-javascript-plugin-2.10.jar", "79dba9cab72d8d31767f47c03d169598", "2.10", true));
    pluginList.add(new DefaultSonarAnalyzer("csharp", "sonar-csharp-plugin-4.4.jar", "e78bc8ac2e376c4a7a2a2cae914bdc52", "4.4", false));
    pluginList.add(new DefaultSonarAnalyzer("groovy", "sonar-groovy-plugin-1.2.jar", "14908dd5f3a9b9d795dbc103f0af546f", "1.2", true));
    pluginList.add(new DefaultSonarAnalyzer("java", "sonar-java-plugin-3.12-SNAPSHOT.jar", "de5308f43260d357acc97712ce4c5475", "3.12-SNAPSHOT", true));

    underTest.fetchPluginsTo(Version.create("7.1"), dest, pluginList, new ProgressWrapper(null));

    PluginReferences pluginReferences = ProtobufUtil.readFile(dest.resolve(StoragePaths.PLUGIN_REFERENCES_PB), PluginReferences.parser());
    assertThat(pluginReferences.getReferenceList()).extracting("key", "hash", "filename")
      .containsOnly(tuple("java", "de5308f43260d357acc97712ce4c5475", "sonar-java-plugin-3.12-SNAPSHOT.jar"),
        tuple("groovy", "14908dd5f3a9b9d795dbc103f0af546f", "sonar-groovy-plugin-1.2.jar"),
        tuple("javascript", "79dba9cab72d8d31767f47c03d169598", "sonar-javascript-plugin-2.10.jar"));

    verify(pluginCache).get(eq("sonar-java-plugin-3.12-SNAPSHOT.jar"), eq("de5308f43260d357acc97712ce4c5475"), any(Copier.class));
  }
}
