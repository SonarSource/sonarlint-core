/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import org.sonarsource.sonarlint.core.container.connected.update.ProjectConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProjectStorageUpdateCheckerTest {

  private static final Version SERVER_VERSION = Version.create("6.0");

  @Rule
  public LogTester logTester = new LogTester();

  private static final String MODULE_KEY = "foo";
  private ProjectStorageUpdateChecker checker;
  private StorageReader storageReader;
  private ProjectConfigurationDownloader projectConfigurationDownloader;

  @Before
  public void prepare() {
    storageReader = mock(StorageReader.class);
    projectConfigurationDownloader = mock(ProjectConfigurationDownloader.class);

    when(storageReader.readProjectConfig(MODULE_KEY)).thenReturn(Sonarlint.ProjectConfiguration.newBuilder().build());
    when(storageReader.readServerInfos()).thenReturn(ServerInfos.newBuilder().setVersion(SERVER_VERSION.toString()).build());
    when(projectConfigurationDownloader.fetch(eq(SERVER_VERSION), eq(MODULE_KEY), any(GlobalProperties.class), any(ProgressWrapper.class)))
      .thenReturn(ProjectConfiguration.newBuilder().build());

    SettingsDownloader settingsDownloader = mock(SettingsDownloader.class);
    when(settingsDownloader.fetchGlobalSettings(SERVER_VERSION)).thenReturn(GlobalProperties.newBuilder().build());
    checker = new ProjectStorageUpdateChecker(storageReader, projectConfigurationDownloader, settingsDownloader);
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
    when(projectConfigurationDownloader.fetch(eq(SERVER_VERSION), eq(MODULE_KEY), any(GlobalProperties.class), any(ProgressWrapper.class)))
      .thenReturn(ProjectConfiguration.newBuilder().putProperties("sonar.issue.enforce.allFiles", "value").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Project settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Property 'sonar.issue.enforce.allFiles' added with value 'value'");
  }

  @Test
  public void removedProp() {
    when(storageReader.readProjectConfig(MODULE_KEY)).thenReturn(Sonarlint.ProjectConfiguration.newBuilder().putProperties("sonar.cobol.license.secured", "value").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Project settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Property 'sonar.cobol.license.secured' removed");
  }

  @Test
  public void changedProp() {
    when(storageReader.readProjectConfig(MODULE_KEY)).thenReturn(ProjectConfiguration.newBuilder().putProperties("sonar.inclusions", "old").build());
    when(projectConfigurationDownloader.fetch(eq(SERVER_VERSION), eq(MODULE_KEY), any(GlobalProperties.class), any(ProgressWrapper.class)))
      .thenReturn(ProjectConfiguration.newBuilder().putProperties("sonar.inclusions", "new").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Project settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Value of property 'sonar.inclusions' changed from 'old' to 'new'");
  }

  @Test
  public void addedProfile() {
    when(projectConfigurationDownloader.fetch(eq(SERVER_VERSION), eq(MODULE_KEY), any(GlobalProperties.class), any(ProgressWrapper.class)))
      .thenReturn(ProjectConfiguration.newBuilder().putQprofilePerLanguage("java", "sonar-way-123").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profiles configuration changed");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Quality profile for language 'java' added with value 'sonar-way-123'");
  }

  @Test
  public void ignoreRemovedProfile() {
    when(storageReader.readProjectConfig(MODULE_KEY)).thenReturn(Sonarlint.ProjectConfiguration.newBuilder().putQprofilePerLanguage("java", "sonar-way-123").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isFalse();
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Quality profile for language 'java' removed");
  }

  @Test
  public void changedQualityProfile() {
    when(projectConfigurationDownloader.fetch(eq(SERVER_VERSION), eq(MODULE_KEY), any(GlobalProperties.class), any(ProgressWrapper.class)))
      .thenReturn(ProjectConfiguration.newBuilder().putQprofilePerLanguage("java", "sonar-way-456").build());
    when(storageReader.readProjectConfig(MODULE_KEY)).thenReturn(ProjectConfiguration.newBuilder().putQprofilePerLanguage("java", "sonar-way-123").build());

    StorageUpdateCheckResult result = checker.checkForUpdates(MODULE_KEY, new ProgressWrapper(null));

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Quality profiles configuration changed");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Quality profile for language 'java' changed from 'sonar-way-123' to 'sonar-way-456'");
  }

}
