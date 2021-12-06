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

import java.nio.file.Path;
import java.util.Collections;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.Settings;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.container.storage.GlobalSettingsStore;
import org.sonarsource.sonarlint.core.container.storage.StorageFolder;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSettingsUpdateCheckerTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();
  @TempDir
  Path tempDir;

  private GlobalSettingsUpdateChecker checker;
  private GlobalSettingsStore globalSettingsStore;

  @BeforeEach
  void prepare() {
    globalSettingsStore = new GlobalSettingsStore(new StorageFolder.Default(tempDir));
    globalSettingsStore.store(Collections.emptyMap());
    Settings.ValuesWsResponse response = Settings.ValuesWsResponse.newBuilder().build();
    mockServer.addProtobufResponse("/api/settings/values.protobuf", response);

    checker = new GlobalSettingsUpdateChecker(mockServer.serverApiHelper());
  }

  @Test
  void testNoChanges() {
    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(globalSettingsStore, result);

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }

  @Test
  void ignore_unused_props() {
    mockServerProperty("sonar.foo", "value");

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(globalSettingsStore, result);

    assertThat(result.needUpdate()).isFalse();
    assertThat(result.changelog()).isEmpty();
  }

  @Test
  void addedProp() {
    mockServerProperty("sonar.test.inclusions", "value");

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(globalSettingsStore, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(Level.DEBUG)).contains("Property 'sonar.test.inclusions' added with value 'value'");
  }

  @Test
  void addedPropObfuscateSecured() {
    mockServerProperty("sonar.java.license.secured", "value");

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(globalSettingsStore, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(Level.DEBUG)).contains("Property 'sonar.java.license.secured' added with value '******'");
  }

  @Test
  void addedPropAbbreviateValue() {
    mockServerProperty("sonar.issue.enforce.allFiles", StringUtils.repeat("abcde", 10));

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(globalSettingsStore, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(Level.DEBUG)).contains("Property 'sonar.issue.enforce.allFiles' added with value '" + StringUtils.repeat("abcde", 3) + "ab...'");
  }

  @Test
  void removedProp() {
    globalSettingsStore.store(Collections.singletonMap("sonar.issue.ignore.allFiles", "value"));

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(globalSettingsStore, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(Level.DEBUG)).contains("Property 'sonar.issue.ignore.allFiles' removed");
  }

  @Test
  void changedProp() {
    globalSettingsStore.store(Collections.singletonMap("sonar.exclusions", "old"));
    mockServerProperty("sonar.exclusions", "new");

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(globalSettingsStore, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(Level.DEBUG)).contains("Value of property 'sonar.exclusions' changed from 'old' to 'new'");
  }

  @Test
  void changedPropDiffAbbreviateEnd() {
    mockServerProperty("sonar.exclusions", "four,five,six,seven,eight");
    globalSettingsStore.store(Collections.singletonMap("sonar.exclusions", "one,two,three,four,five,six,seven,eight"));

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(globalSettingsStore, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(Level.DEBUG)).contains("Value of property 'sonar.exclusions' changed from 'one,two,three,fou...' to 'four,five,six,sev...'");
  }

  @Test
  void changedPropDiffAbbreviateBeginEnd() {
    globalSettingsStore.store(Collections.singletonMap("sonar.exclusions", "one,two,three,four,five,six,seven,eight"));
    mockServerProperty("sonar.exclusions", "one,four,five,six,seven,eight");

    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();
    checker.checkForUpdates(globalSettingsStore, result);

    assertThat(result.needUpdate()).isTrue();
    assertThat(result.changelog()).containsOnly("Global settings updated");
    assertThat(logTester.logs(Level.DEBUG)).contains("Value of property 'sonar.exclusions' changed from '...two,three,four...' to '...four,five,six,...'");
  }

  private void mockServerProperty(String propertyKey, String propertyValue) {
    Settings.ValuesWsResponse response = Settings.ValuesWsResponse.newBuilder()
      .addSettings(Settings.Setting.newBuilder().setKey(propertyKey).setValue(propertyValue)).build();
    mockServer.addProtobufResponse("/api/settings/values.protobuf", response);
  }
}
