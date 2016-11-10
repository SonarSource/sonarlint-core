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

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles.QProfile;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlobalUpdateCheckerTest {

  private GlobalUpdateChecker checker;
  private ServerVersionAndStatusChecker statusChecker;
  private StorageManager storageManager;
  private GlobalPropertiesDownloader globalPropertiesDownloader;
  private PluginReferencesDownloader pluginReferenceDownloader;
  private QualityProfilesDownloader qualityProfilesDownloader;

  @Before
  public void prepare() {
    statusChecker = mock(ServerVersionAndStatusChecker.class);
    when(statusChecker.checkVersionAndStatus()).thenReturn(ServerInfos.newBuilder().build());

    storageManager = mock(StorageManager.class);
    globalPropertiesDownloader = mock(GlobalPropertiesDownloader.class);
    pluginReferenceDownloader = mock(PluginReferencesDownloader.class);
    qualityProfilesDownloader = mock(QualityProfilesDownloader.class);

    when(storageManager.readGlobalPropertiesFromStorage()).thenReturn(GlobalProperties.newBuilder().build());
    when(storageManager.readPluginReferencesFromStorage()).thenReturn(PluginReferences.newBuilder().build());
    when(storageManager.readQProfilesFromStorage()).thenReturn(QProfiles.newBuilder().build());
    when(globalPropertiesDownloader.fetchGlobalProperties()).thenReturn(GlobalProperties.newBuilder().build());
    when(pluginReferenceDownloader.fetchPlugins(anyString())).thenReturn(PluginReferences.newBuilder().build());
    when(qualityProfilesDownloader.fetchQualityProfiles()).thenReturn(QProfiles.newBuilder().build());

    checker = new GlobalUpdateChecker(storageManager, mock(PluginVersionChecker.class), statusChecker,
      pluginReferenceDownloader, globalPropertiesDownloader, qualityProfilesDownloader);
  }

  @Test
  public void testNoChanges() {
    GlobalStorageUpdateCheckResult result = checker.checkForUpdate(mock(ProgressWrapper.class));

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }

  @Test
  public void addedProp() {
    when(globalPropertiesDownloader.fetchGlobalProperties()).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.new", "value").build());

    GlobalStorageUpdateCheckResult result = checker.checkForUpdate(mock(ProgressWrapper.class));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Property 'sonar.new' added with value 'value'");
  }

  @Test
  public void removedProp() {
    when(storageManager.readGlobalPropertiesFromStorage()).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.old", "value").build());

    GlobalStorageUpdateCheckResult result = checker.checkForUpdate(mock(ProgressWrapper.class));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Property 'sonar.old' removed");
  }

  @Test
  public void changedProp() {
    when(storageManager.readGlobalPropertiesFromStorage()).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.prop", "old").build());
    when(globalPropertiesDownloader.fetchGlobalProperties()).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.prop", "new").build());

    GlobalStorageUpdateCheckResult result = checker.checkForUpdate(mock(ProgressWrapper.class));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Value of property 'sonar.prop' changed from 'old' to 'new'");
  }

  @Test
  public void addedPlugin() {
    when(pluginReferenceDownloader.fetchPlugins(anyString()))
      .thenReturn(PluginReferences.newBuilder().addReference(PluginReference.newBuilder().setKey("java").setHash("123").build()).build());

    GlobalStorageUpdateCheckResult result = checker.checkForUpdate(mock(ProgressWrapper.class));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Plugin 'java' added");
  }

  @Test
  public void removedPlugin() {
    when(storageManager.readPluginReferencesFromStorage())
      .thenReturn(PluginReferences.newBuilder().addReference(PluginReference.newBuilder().setKey("java").setHash("123").build()).build());

    GlobalStorageUpdateCheckResult result = checker.checkForUpdate(mock(ProgressWrapper.class));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Plugin 'java' removed");
  }

  @Test
  public void updatedPlugin() {
    when(pluginReferenceDownloader.fetchPlugins(anyString()))
      .thenReturn(PluginReferences.newBuilder().addReference(PluginReference.newBuilder().setKey("java").setHash("123").build()).build());
    when(storageManager.readPluginReferencesFromStorage())
      .thenReturn(PluginReferences.newBuilder().addReference(PluginReference.newBuilder().setKey("java").setHash("456").build()).build());

    GlobalStorageUpdateCheckResult result = checker.checkForUpdate(mock(ProgressWrapper.class));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Plugin 'java' updated");
  }

  @Test
  public void addedQProfile() {
    when(qualityProfilesDownloader.fetchQualityProfiles())
      .thenReturn(QProfiles.newBuilder().putQprofilesByKey("java-123", QProfile.newBuilder().setKey("java-123").build()).build());

    GlobalStorageUpdateCheckResult result = checker.checkForUpdate(mock(ProgressWrapper.class));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile 'java-123' added");
  }

  @Test
  public void removedQProfile() {
    when(storageManager.readQProfilesFromStorage())
      .thenReturn(QProfiles.newBuilder().putQprofilesByKey("java-123", QProfile.newBuilder().setKey("java-123").build()).build());

    GlobalStorageUpdateCheckResult result = checker.checkForUpdate(mock(ProgressWrapper.class));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile 'java-123' removed");
  }

  @Test
  public void updatedQProfile() {
    when(qualityProfilesDownloader.fetchQualityProfiles())
      .thenReturn(QProfiles.newBuilder().putQprofilesByKey("java-123", QProfile.newBuilder().setKey("java-123").setRulesUpdatedAt("foo").build()).build());
    when(storageManager.readQProfilesFromStorage())
      .thenReturn(QProfiles.newBuilder().putQprofilesByKey("java-123", QProfile.newBuilder().setKey("java-123").setRulesUpdatedAt("foo2").build()).build());

    GlobalStorageUpdateCheckResult result = checker.checkForUpdate(mock(ProgressWrapper.class));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile 'java-123' updated");
  }

}
