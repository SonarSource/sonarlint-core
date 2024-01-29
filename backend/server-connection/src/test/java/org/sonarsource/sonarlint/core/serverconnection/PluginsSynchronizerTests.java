/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2023 SonarSource SA
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
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;

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

    underTest = new PluginsSynchronizer(Set.of(SonarLanguage.SECRETS), new ConnectionStorage(dest, dest, "connectionId"), Set.of("text"));
    var anyPluginUpdated = underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), false, new SonarLintCancelMonitor());

    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/plugin_references.pb")).doesNotExist();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-text-plugin-1.2.3.4.jar")).doesNotExist();
    assertThat(anyPluginUpdated).isFalse();
  }

  /**
   * Emulating SonarQube 10.4 where custom secrets are enabled and `sonar-text` is embedded but will be downloaded
   * alongside `sonar-text-enterprise` because it is now supporting SonarLint!
   */
  @Test
  void should_not_synchronize_sonar_text_post_103(@TempDir Path dest) {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": [" +
      "{\"key\": \"text\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-text-plugin-1.2.3.4.jar\", \"sonarLintSupported\": true}," +
      "{\"key\": \"textenterprise\", \"hash\": \"de5308f43260d357acc97712ce4c5475\", \"filename\": \"sonar-text-enterprise-plugin-5.6.7.8.jar\", \"sonarLintSupported\": true}" +
      "]}");
    mockServer.addStringResponse("/api/plugins/download?plugin=text", "content-text");
    mockServer.addStringResponse("/api/plugins/download?plugin=textenterprise", "content-textenterprise");

    underTest = new PluginsSynchronizer(Set.of(SonarLanguage.SECRETS), new ConnectionStorage(dest, dest, "connectionId"), Set.of("text"));
    var anyPluginUpdated = underTest.synchronize(new ServerApi(mockServer.serverApiHelper()), true, new SonarLintCancelMonitor());

    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/plugin_references.pb")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-text-plugin-1.2.3.4.jar")).exists();
    assertThat(dest.resolve("636f6e6e656374696f6e4964/plugins/sonar-text-enterprise-plugin-5.6.7.8.jar")).exists();
    assertThat(anyPluginUpdated).isTrue();
  }

}