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
package org.sonarsource.sonarlint.core.container.connected.update.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedList;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;

public class PluginsUpdateCheckerTest {

  private PluginsUpdateChecker checker;
  private StorageReader storageReader;
  private PluginReferencesDownloader pluginReferenceDownloader;

  @Before
  public void prepare() {

    storageReader = mock(StorageReader.class);
    pluginReferenceDownloader = mock(PluginReferencesDownloader.class);

    when(storageReader.readPluginReferences()).thenReturn(PluginReferences.newBuilder().build());
    when(pluginReferenceDownloader.fetchPlugins(anyList())).thenReturn(PluginReferences.newBuilder().build());

    checker = new PluginsUpdateChecker(storageReader, pluginReferenceDownloader);
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
    when(pluginReferenceDownloader.fetchPlugins(anyList()))
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
    when(pluginReferenceDownloader.fetchPlugins(anyList()))
      .thenReturn(PluginReferences.newBuilder().addReference(PluginReference.newBuilder().setKey("java").setHash("123").build()).build());
    when(storageReader.readPluginReferences())
      .thenReturn(PluginReferences.newBuilder().addReference(PluginReference.newBuilder().setKey("java").setHash("456").build()).build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result, new LinkedList<>());

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Plugin 'java' updated");
  }

}
