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
package org.sonarsource.sonarlint.core.container.connected.update.check;

import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PluginsUpdateCheckerTest {

  private PluginsUpdateChecker checker;
  private StorageReader storageReader;
  private PluginReferencesDownloader pluginReferenceDownloader;
  private final HashMap<String, URL> embeddedPlugins = new HashMap<>();

  @Before
  public void prepare() {

    storageReader = mock(StorageReader.class);
    pluginReferenceDownloader = mock(PluginReferencesDownloader.class);

    when(storageReader.readPluginReferences()).thenReturn(PluginReferences.newBuilder().build());
    when(pluginReferenceDownloader.toReferences(anyList())).thenReturn(PluginReferences.newBuilder().build());

    ConnectedGlobalConfiguration config = mock(ConnectedGlobalConfiguration.class);
    when(config.getEmbeddedPluginUrlsByKey()).thenReturn(embeddedPlugins);
    checker = new PluginsUpdateChecker(storageReader, pluginReferenceDownloader, config);
  }

  @Test
  public void testNoChanges() {
    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result, new LinkedList<>());

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }

  @Test
  public void addedPlugin() {
    when(pluginReferenceDownloader.toReferences(anyList()))
      .thenReturn(PluginReferences.newBuilder().addReference(PluginReference.newBuilder().setKey("java").setHash("123").build()).build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result, new LinkedList<>());

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Plugin 'java' added");
  }

  @Test
  public void removedPlugin() {
    when(storageReader.readPluginReferences())
      .thenReturn(PluginReferences.newBuilder().addReference(PluginReference.newBuilder().setKey("java").setHash("123").build()).build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result, new LinkedList<>());

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Plugin 'java' removed");
  }

  @Test
  public void updatedPlugin() {
    when(pluginReferenceDownloader.toReferences(anyList()))
      .thenReturn(PluginReferences.newBuilder().addReference(PluginReference.newBuilder().setKey("java").setHash("123").build()).build());
    when(storageReader.readPluginReferences())
      .thenReturn(PluginReferences.newBuilder().addReference(PluginReference.newBuilder().setKey("java").setHash("456").build()).build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result, new LinkedList<>());

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Plugin 'java' updated");
  }

  @Test
  public void ignoreEmbeddedPlugins() throws Exception {
    embeddedPlugins.put("java", new URL("file://java.jar"));
    when(pluginReferenceDownloader.toReferences(anyList()))
      .thenReturn(PluginReferences.newBuilder().addReference(PluginReference.newBuilder().setKey("java").setHash("").build()).build());
    when(storageReader.readPluginReferences())
      .thenReturn(PluginReferences.newBuilder().addReference(PluginReference.newBuilder().setKey("java").setHash("").build()).build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result, new LinkedList<>());

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }

}
