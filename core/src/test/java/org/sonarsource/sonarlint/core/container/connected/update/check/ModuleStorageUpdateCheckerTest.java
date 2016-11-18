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
package org.sonarsource.sonarlint.core.container.connected.update.check;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.PropertiesDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModuleStorageUpdateCheckerTest {

  private static final String MODULE_KEY = "foo";
  private ModuleStorageUpdateChecker checker;
  private StorageManager storageManager;
  private ModuleConfigurationDownloader moduleConfigurationDownloader;

  @Before
  public void prepare() {
    storageManager = mock(StorageManager.class);
    moduleConfigurationDownloader = mock(ModuleConfigurationDownloader.class);

    when(storageManager.readModuleConfigFromStorage(MODULE_KEY)).thenReturn(ModuleConfiguration.newBuilder().build());
    when(moduleConfigurationDownloader.fetchModuleConfiguration(eq(MODULE_KEY), any(GlobalProperties.class))).thenReturn(ModuleConfiguration.newBuilder().build());

    PropertiesDownloader globalPropertiesDownloader = mock(PropertiesDownloader.class);
    when(globalPropertiesDownloader.fetchGlobalProperties()).thenReturn(GlobalProperties.newBuilder().build());

    checker = new ModuleStorageUpdateChecker(storageManager, moduleConfigurationDownloader, globalPropertiesDownloader);
  }

  @Test
  public void testNoChanges() {
    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, null);

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }

  @Test
  public void addedProp() {
    when(moduleConfigurationDownloader.fetchModuleConfiguration(eq(MODULE_KEY), any(GlobalProperties.class)))
      .thenReturn(ModuleConfiguration.newBuilder().putProperties("sonar.issue.enforce.allFiles", "value").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, null);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Property 'sonar.issue.enforce.allFiles' added with value 'value'");
  }

  @Test
  public void removedProp() {
    when(storageManager.readModuleConfigFromStorage(MODULE_KEY)).thenReturn(ModuleConfiguration.newBuilder().putProperties("sonar.cobol.license.secured", "value").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, null);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Property 'sonar.cobol.license.secured' removed");
  }

  @Test
  public void changedProp() {
    when(storageManager.readModuleConfigFromStorage(MODULE_KEY)).thenReturn(ModuleConfiguration.newBuilder().putProperties("sonar.inclusions", "old").build());
    when(moduleConfigurationDownloader.fetchModuleConfiguration(eq(MODULE_KEY), any(GlobalProperties.class)))
      .thenReturn(ModuleConfiguration.newBuilder().putProperties("sonar.inclusions", "new").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, null);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Value of property 'sonar.inclusions' changed from 'old' to 'new'");
  }

  @Test
  public void addedProfile() {
    when(moduleConfigurationDownloader.fetchModuleConfiguration(eq(MODULE_KEY), any(GlobalProperties.class)))
      .thenReturn(ModuleConfiguration.newBuilder().putQprofilePerLanguage("java", "sonar-way-123").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, null);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile for language 'java' added with value 'sonar-way-123'");
  }

  @Test
  public void removedProfile() {
    when(storageManager.readModuleConfigFromStorage(MODULE_KEY)).thenReturn(ModuleConfiguration.newBuilder().putQprofilePerLanguage("java", "sonar-way-123").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, null);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile for language 'java' removed");
  }

  @Test
  public void changedQualityProfile() {
    when(moduleConfigurationDownloader.fetchModuleConfiguration(eq(MODULE_KEY), any(GlobalProperties.class)))
      .thenReturn(ModuleConfiguration.newBuilder().putQprofilePerLanguage("java", "sonar-way-456").build());
    when(storageManager.readModuleConfigFromStorage(MODULE_KEY)).thenReturn(ModuleConfiguration.newBuilder().putQprofilePerLanguage("java", "sonar-way-123").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, null);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profile for language 'java' changed from 'sonar-way-123' to 'sonar-way-456'");
  }

}
