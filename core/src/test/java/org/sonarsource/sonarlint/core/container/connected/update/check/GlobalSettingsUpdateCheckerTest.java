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
import org.sonarsource.sonarlint.core.container.connected.update.PropertiesDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlobalSettingsUpdateCheckerTest {

  private GlobalSettingsUpdateChecker checker;
  private StorageManager storageManager;
  private PropertiesDownloader globalPropertiesDownloader;

  @Before
  public void prepare() {
    storageManager = mock(StorageManager.class);
    globalPropertiesDownloader = mock(PropertiesDownloader.class);

    when(storageManager.readGlobalPropertiesFromStorage()).thenReturn(GlobalProperties.newBuilder().build());
    when(globalPropertiesDownloader.fetchGlobalProperties()).thenReturn(GlobalProperties.newBuilder().build());

    checker = new GlobalSettingsUpdateChecker(storageManager, globalPropertiesDownloader);
  }

  @Test
  public void testNoChanges() {
    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result);

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }

  @Test
  public void ignore_unused_props() {
    when(globalPropertiesDownloader.fetchGlobalProperties()).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.foo", "value").build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result);

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }

  @Test
  public void addedProp() {
    when(globalPropertiesDownloader.fetchGlobalProperties()).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.test.inclusions", "value").build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Property 'sonar.test.inclusions' added with value 'value'");
  }

  @Test
  public void removedProp() {
    when(storageManager.readGlobalPropertiesFromStorage()).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.issue.ignore.allFiles", "value").build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Property 'sonar.issue.ignore.allFiles' removed");
  }

  @Test
  public void changedProp() {
    when(storageManager.readGlobalPropertiesFromStorage()).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.exclusions", "old").build());
    when(globalPropertiesDownloader.fetchGlobalProperties()).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.exclusions", "new").build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Value of property 'sonar.exclusions' changed from 'old' to 'new'");
  }

}
