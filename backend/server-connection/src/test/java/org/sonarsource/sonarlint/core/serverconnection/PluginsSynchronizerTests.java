/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.monitoring.DogfoodEnvironmentDetectionService;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PluginsSynchronizerTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();
  private PluginsSynchronizer underTest;

  /**
   * Emulating SonarQube 10.3 where custom secrets are not enabled and `sonar-text` is embedded and
   * `sonar-text-enterprise` not downloaded because it is not supporting SonarLint yet!
   */
  @Test
  void should_not_synchronize_sonar_text_pre_104(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"text\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-text-plugin-1.2.3.4.jar\", \"sonarLintSupported\": true}," +
      "{\"key\": \"textenterprise\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-text-enterprise-plugin-5.6.7.8.jar\", \"sonarLintSupported\": false}" +
      "]}");

    var dogfoodEnvDetectionService = mock(DogfoodEnvironmentDetectionService.class);
    var databaseService = mock(SonarLintDatabase.class);
    underTest = new PluginsSynchronizer(Set.of(SonarLanguage.SECRETS), new ConnectionStorage(dest, dest, "connectionId", dogfoodEnvDetectionService, databaseService), Set.of("text"));
    underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), Version.create("10.3"), new SonarLintCancelMonitor());

    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/plugin_references.pb")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-text-plugin-1.2.3.4.jar")).doesNotExist();
  }

  /**
   * Emulating SonarQube 10.4 where custom secrets are enabled and `sonar-text` is embedded but will be downloaded
   * alongside `sonar-text-enterprise` because it is now supporting SonarLint!
   */
  @Test
  void should_synchronize_sonar_text_post_103(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"text\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-text-plugin-2.3.4.5.jar\", \"sonarLintSupported\": true}," +
      "{\"key\": \"textenterprise\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-text-enterprise-plugin-5.6.7.8.jar\", \"sonarLintSupported\": true}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=text", "content-text");
    mockServer.addStringResponse("/api/plugins/download?plugin=textenterprise", "content-textenterprise");
    var dogfoodEnvDetectionService = mock(DogfoodEnvironmentDetectionService.class);
    var databaseService = mock(SonarLintDatabase.class);

    underTest = new PluginsSynchronizer(Set.of(SonarLanguage.SECRETS), new ConnectionStorage(dest, dest, "connectionId", dogfoodEnvDetectionService, databaseService), Set.of("text"));
    underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), Version.create("10.4"), new SonarLintCancelMonitor());

    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/plugin_references.pb")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-text-plugin-2.3.4.5.jar")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-text-enterprise-plugin-5.6.7.8.jar")).exists();
  }

  /**
   * Emulating SonarQube 2025.2 `sonar-go` is embedded but `sonar-go-enterprise` will be downloaded
   */
  @Test
  void should_synchronize_sonar_go_enterprise_in_2025_2(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"text\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-text-plugin-2.3.4.5.jar\", \"sonarLintSupported\": true}," +
      "{\"key\": \"textenterprise\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-text-enterprise-plugin-5.6.7.8.jar\", \"sonarLintSupported\": true}," +
      "{\"key\": \"goenterprise\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-go-enterprise-plugin-1.2.3.4.jar\", \"sonarLintSupported\": false}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=text", "content-text");
    mockServer.addStringResponse("/api/plugins/download?plugin=textenterprise", "content-textenterprise");
    mockServer.addStringResponse("/api/plugins/download?plugin=goenterprise", "content-goenterprise");
    var dogfoodEnvDetectionService = mock(DogfoodEnvironmentDetectionService.class);
    var databaseService = mock(SonarLintDatabase.class);

    underTest = new PluginsSynchronizer(Set.of(SonarLanguage.SECRETS, SonarLanguage.GO), new ConnectionStorage(dest, dest, "connectionId", dogfoodEnvDetectionService, databaseService), Set.of("text", "go"));
    underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), Version.create("2025.2"), new SonarLintCancelMonitor());

    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/plugin_references.pb")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-text-plugin-2.3.4.5.jar")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-text-enterprise-plugin-5.6.7.8.jar")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-go-enterprise-plugin-1.2.3.4.jar")).exists();
  }

  @Test
  void should_not_synchronize_sonar_go_enterprise_in_2025_2_if_language_not_enabled(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"goenterprise\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-go-enterprise-plugin-1.2.3.4.jar\", \"sonarLintSupported\": false}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=goenterprise", "content-goenterprise");
    var dogfoodEnvDetectionService = mock(DogfoodEnvironmentDetectionService.class);
    var databaseService = mock(SonarLintDatabase.class);

    underTest = new PluginsSynchronizer(Set.of(SonarLanguage.SECRETS), new ConnectionStorage(dest, dest, "connectionId", dogfoodEnvDetectionService, databaseService), Set.of("text", "go"));
    underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), Version.create("2025.2"), new SonarLintCancelMonitor());

    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/plugin_references.pb")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-go-enterprise-plugin-1.2.3.4.jar")).doesNotExist();
  }

  /**
   * Emulating SonarQube 2025.3 where `sonar-go` is embedded but the `sonar-go` from the server will be downloaded
   */
  @Test
  void should_synchronize_sonar_go_in_2025_3(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"text\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-text-plugin-2.3.4.5.jar\", \"sonarLintSupported\": true}," +
      "{\"key\": \"textenterprise\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-text-enterprise-plugin-5.6.7.8.jar\", \"sonarLintSupported\": true}," +
      "{\"key\": \"go\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-go-plugin-1.2.3.4.jar\", \"sonarLintSupported\": true}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=text", "content-text");
    mockServer.addStringResponse("/api/plugins/download?plugin=textenterprise", "content-textenterprise");
    mockServer.addStringResponse("/api/plugins/download?plugin=go", "content-go");
    var dogfoodEnvDetectionService = mock(DogfoodEnvironmentDetectionService.class);
    var databaseService = mock(SonarLintDatabase.class);

    underTest = new PluginsSynchronizer(Set.of(SonarLanguage.SECRETS, SonarLanguage.GO), new ConnectionStorage(dest, dest, "connectionId", dogfoodEnvDetectionService, databaseService), Set.of("text", "go"));
    underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), Version.create("2025.2"), new SonarLintCancelMonitor());

    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/plugin_references.pb")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-text-plugin-2.3.4.5.jar")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-text-enterprise-plugin-5.6.7.8.jar")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-go-plugin-1.2.3.4.jar")).exists();
  }

  @Test
  void should_not_synchronize_sonar_go_in_2025_3_if_language_not_enabled(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"go\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-go-plugin-1.2.3.4.jar\", \"sonarLintSupported\": true}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=go", "content-go");
    var dogfoodEnvDetectionService = mock(DogfoodEnvironmentDetectionService.class);
    var databaseService = mock(SonarLintDatabase.class);

    underTest = new PluginsSynchronizer(Set.of(SonarLanguage.SECRETS), new ConnectionStorage(dest, dest, "connectionId", dogfoodEnvDetectionService, databaseService), Set.of("text", "go"));
    underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), Version.create("2025.2"), new SonarLintCancelMonitor());

    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/plugin_references.pb")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-go-plugin-1.2.3.4.jar")).doesNotExist();
  }

  @Test
  void should_synchronize_csharp_enterprise_if_language_enabled(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"csharpenterprise\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-csharpenterprise-plugin-1.2.3.4.jar\", \"sonarLintSupported\": false}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=csharpenterprise", "content-go");
    var dogfoodEnvDetectionService = mock(DogfoodEnvironmentDetectionService.class);
    var databaseService = mock(SonarLintDatabase.class);

    underTest = new PluginsSynchronizer(Set.of(SonarLanguage.CS), new ConnectionStorage(dest, dest, "connectionId", dogfoodEnvDetectionService, databaseService), Set.of());
    underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), Version.create("2025.2"), new SonarLintCancelMonitor());

    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/plugin_references.pb")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-csharpenterprise-plugin-1.2.3.4.jar")).exists();
  }

  @Test
  void should_not_synchronize_csharp_enterprise_if_language_disabled(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"csharpenterprise\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-csharpenterprise-plugin-1.2.3.4.jar\", \"sonarLintSupported\": false}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=csharpenterprise", "content-csharp");
    var dogfoodEnvDetectionService = mock(DogfoodEnvironmentDetectionService.class);
    var databaseService = mock(SonarLintDatabase.class);

    underTest = new PluginsSynchronizer(Set.of(SonarLanguage.GO), new ConnectionStorage(dest, dest, "connectionId", dogfoodEnvDetectionService, databaseService), Set.of());
    underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), Version.create("2025.2"), new SonarLintCancelMonitor());

    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/plugin_references.pb")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-csharpenterprise-plugin-1.2.3.4.jar")).doesNotExist();
  }

  @Test
  void should_synchronize_vbnet_enterprise_if_language_enabled(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"vbnetenterprise\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-vbnetenterprise-plugin-1.2.3.4.jar\", \"sonarLintSupported\": false}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=vbnetenterprise", "content-vb");
    var dogfoodEnvDetectionService = mock(DogfoodEnvironmentDetectionService.class);
    var databaseService = mock(SonarLintDatabase.class);
    underTest = new PluginsSynchronizer(Set.of(SonarLanguage.VBNET), new ConnectionStorage(dest, dest, "connectionId",
      dogfoodEnvDetectionService, databaseService), Set.of());
    underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), Version.create("2025.2"), new SonarLintCancelMonitor());

    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/plugin_references.pb")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-vbnetenterprise-plugin-1.2.3.4.jar")).exists();
  }

  @Test
  void should_not_synchronize_vbnet_enterprise_if_language_disabled(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"vbnetenterprise\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-vbnetenterprise-plugin-1.2.3.4.jar\", \"sonarLintSupported\": false}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=vbnetenterprise", "content-go");
    var dogfoodEnvDetectionService = mock(DogfoodEnvironmentDetectionService.class);
    var databaseService = mock(SonarLintDatabase.class);

    underTest = new PluginsSynchronizer(Set.of(SonarLanguage.GO), new ConnectionStorage(dest, dest, "connectionId",
      dogfoodEnvDetectionService, databaseService), Set.of());
    underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), Version.create("2025.2"), new SonarLintCancelMonitor());

    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/plugin_references.pb")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-vbnetenterprise-plugin-1.2.3.4.jar")).doesNotExist();
  }
}
