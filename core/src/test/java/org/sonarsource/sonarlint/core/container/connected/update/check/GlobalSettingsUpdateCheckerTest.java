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

import org.apache.commons.lang.StringUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonarsource.sonarlint.core.container.connected.update.SettingsDownloader;
import org.sonarsource.sonarlint.core.container.storage.StorageReader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlobalSettingsUpdateCheckerTest {

  private static final String SQ_VERSION = "6.3";
  private GlobalSettingsUpdateChecker checker;
  private StorageReader storageReader;
  private SettingsDownloader globalPropertiesDownloader;

  @Rule
  public LogTester logTester = new LogTester();

  @Before
  public void prepare() {
    storageReader = mock(StorageReader.class);
    globalPropertiesDownloader = mock(SettingsDownloader.class);

    when(storageReader.readGlobalProperties()).thenReturn(GlobalProperties.newBuilder().build());
    when(globalPropertiesDownloader.fetchGlobalSettings(anyString())).thenReturn(GlobalProperties.newBuilder().build());

    checker = new GlobalSettingsUpdateChecker(storageReader, globalPropertiesDownloader);
  }

  @AfterClass
  public static void after() {
    // to avoid conflicts with SonarLintLogging
    new LogTester().setLevel(LoggerLevel.TRACE);
  }

  @Test
  public void testNoChanges() {
    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(SQ_VERSION, result);

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }

  @Test
  public void ignore_unused_props() {
    when(globalPropertiesDownloader.fetchGlobalSettings(SQ_VERSION)).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.foo", "value").build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(SQ_VERSION, result);

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  public void addedProp() {
    when(globalPropertiesDownloader.fetchGlobalSettings(SQ_VERSION)).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.test.inclusions", "value").build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(SQ_VERSION, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Property 'sonar.test.inclusions' added with value 'value'");
  }

  @Test
  public void addedPropObfuscateSecured() {
    when(globalPropertiesDownloader.fetchGlobalSettings(SQ_VERSION)).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.java.license.secured", "value").build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(SQ_VERSION, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Property 'sonar.java.license.secured' added with value '******'");
  }

  @Test
  public void addedPropAbbreviateValue() {
    when(globalPropertiesDownloader.fetchGlobalSettings(SQ_VERSION))
      .thenReturn(GlobalProperties.newBuilder().putProperties("sonar.issue.enforce.allFiles", StringUtils.repeat("abcde", 10)).build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(SQ_VERSION, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Property 'sonar.issue.enforce.allFiles' added with value '" + StringUtils.repeat("abcde", 3) + "ab...'");
  }

  @Test
  public void removedProp() {
    when(storageReader.readGlobalProperties()).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.issue.ignore.allFiles", "value").build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(SQ_VERSION, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Property 'sonar.issue.ignore.allFiles' removed");
  }

  @Test
  public void changedProp() {
    when(storageReader.readGlobalProperties()).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.exclusions", "old").build());
    when(globalPropertiesDownloader.fetchGlobalSettings(SQ_VERSION)).thenReturn(GlobalProperties.newBuilder().putProperties("sonar.exclusions", "new").build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(SQ_VERSION, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Value of property 'sonar.exclusions' changed from 'old' to 'new'");
  }

  @Test
  public void changedPropDiffAbbreviateEnd() {
    when(storageReader.readGlobalProperties())
      .thenReturn(GlobalProperties.newBuilder().putProperties("sonar.exclusions", "one,two,three,four,five,six,seven,eight").build());
    when(globalPropertiesDownloader.fetchGlobalSettings(SQ_VERSION))
      .thenReturn(GlobalProperties.newBuilder().putProperties("sonar.exclusions", "four,five,six,seven,eight").build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(SQ_VERSION, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Value of property 'sonar.exclusions' changed from 'one,two,three,fou...' to 'four,five,six,sev...'");
  }

  @Test
  public void changedPropDiffAbbreviateBeginEnd() {
    when(storageReader.readGlobalProperties())
      .thenReturn(GlobalProperties.newBuilder().putProperties("sonar.exclusions", "one,two,three,four,five,six,seven,eight").build());
    when(globalPropertiesDownloader.fetchGlobalSettings(SQ_VERSION))
      .thenReturn(GlobalProperties.newBuilder().putProperties("sonar.exclusions", "one,four,five,six,seven,eight").build());

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(SQ_VERSION, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly("Value of property 'sonar.exclusions' changed from '...two,three,four...' to '...four,five,six,...'");
  }

}
