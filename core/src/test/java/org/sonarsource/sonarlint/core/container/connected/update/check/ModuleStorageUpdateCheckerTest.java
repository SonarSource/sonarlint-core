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

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModuleStorageUpdateCheckerTest {

  private static final String SERVER_VERSION = "6.0";

  @Rule
  public LogTester logTester = new LogTester();

  private static final String MODULE_KEY = "foo";
  private ModuleStorageUpdateChecker checker;
  private StorageReader storageReader;
  private ModuleConfigurationDownloader moduleConfigurationDownloader;

  @Before
  public void prepare() {
    storageReader = mock(StorageReader.class);
    moduleConfigurationDownloader = mock(ModuleConfigurationDownloader.class);

    when(storageReader.readModuleConfig(MODULE_KEY)).thenReturn(ModuleConfiguration.newBuilder().build());
    when(storageReader.readServerInfos()).thenReturn(ServerInfos.newBuilder().setVersion(SERVER_VERSION).build());
    when(moduleConfigurationDownloader.fetchModuleConfiguration(eq(SERVER_VERSION), eq(MODULE_KEY), any(GlobalProperties.class), any(ProgressWrapper.class)))
      .thenReturn(ModuleConfiguration.newBuilder().build());

    SettingsDownloader settingsDownloader = mock(SettingsDownloader.class);
    when(settingsDownloader.fetchGlobalSettings(SERVER_VERSION)).thenReturn(GlobalProperties.newBuilder().build());
    checker = new ModuleStorageUpdateChecker(storageReader, moduleConfigurationDownloader, settingsDownloader);
  }

  @AfterClass
  public static void after() {
    // to avoid conflicts with SonarLintLogging
    new LogTester().setLevel(LoggerLevel.TRACE);
  }

  @Test
  public void testNoChanges() {
    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }

  @Test
  public void addedProp() {
    when(moduleConfigurationDownloader.fetchModuleConfiguration(eq(SERVER_VERSION), eq(MODULE_KEY), any(GlobalProperties.class), any(ProgressWrapper.class)))
      .thenReturn(ModuleConfiguration.newBuilder().putProperties("sonar.issue.enforce.allFiles", "value").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Project settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Property 'sonar.issue.enforce.allFiles' added with value 'value'");
  }

  @Test
  public void removedProp() {
    when(storageReader.readModuleConfig(MODULE_KEY)).thenReturn(ModuleConfiguration.newBuilder().putProperties("sonar.cobol.license.secured", "value").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Project settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Property 'sonar.cobol.license.secured' removed");
  }

  @Test
  public void changedProp() {
    when(storageReader.readModuleConfig(MODULE_KEY)).thenReturn(ModuleConfiguration.newBuilder().putProperties("sonar.inclusions", "old").build());
    when(moduleConfigurationDownloader.fetchModuleConfiguration(eq(SERVER_VERSION), eq(MODULE_KEY), any(GlobalProperties.class), any(ProgressWrapper.class)))
      .thenReturn(ModuleConfiguration.newBuilder().putProperties("sonar.inclusions", "new").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Project settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Value of property 'sonar.inclusions' changed from 'old' to 'new'");
  }

  @Test
  public void addedProfile() {
    when(moduleConfigurationDownloader.fetchModuleConfiguration(eq(SERVER_VERSION), eq(MODULE_KEY), any(GlobalProperties.class), any(ProgressWrapper.class)))
      .thenReturn(ModuleConfiguration.newBuilder().putQprofilePerLanguage("java", "sonar-way-123").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profiles configuration changed");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Quality profile for language 'java' added with value 'sonar-way-123'");
  }

  @Test
  public void ignoreRemovedProfile() {
    when(storageReader.readModuleConfig(MODULE_KEY)).thenReturn(ModuleConfiguration.newBuilder().putQprofilePerLanguage("java", "sonar-way-123").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isFalse();
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Quality profile for language 'java' removed");
  }

  @Test
  public void changedQualityProfile() {
    when(moduleConfigurationDownloader.fetchModuleConfiguration(eq(SERVER_VERSION), eq(MODULE_KEY), any(GlobalProperties.class), any(ProgressWrapper.class)))
      .thenReturn(ModuleConfiguration.newBuilder().putQprofilePerLanguage("java", "sonar-way-456").build());
    when(storageReader.readModuleConfig(MODULE_KEY)).thenReturn(ModuleConfiguration.newBuilder().putQprofilePerLanguage("java", "sonar-way-123").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profiles configuration changed");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Quality profile for language 'java' changed from 'sonar-way-123' to 'sonar-way-456'");
  }

}
