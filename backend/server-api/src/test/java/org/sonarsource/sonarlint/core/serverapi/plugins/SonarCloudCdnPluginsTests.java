/*
 * SonarLint Core - Server API
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverapi.plugins;

import java.io.ByteArrayInputStream;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarCloudCdnPluginsTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void should_return_sonarcloud_plugin_content() {
    var helper = mock(ServerApiHelper.class);
    var response = mock(HttpClient.Response.class);
    var cancelMonitor = new SonarLintCancelMonitor();
    when(helper.getBaseUrl()).thenReturn("https://sonarcloud.io");
    when(helper.getAnonymousUrl("https://scanner.sonarcloud.io/plugins/pluginKey/versions/hash.jar", cancelMonitor)).thenReturn(response);
    when(response.bodyAsStream()).thenReturn(new ByteArrayInputStream("content".getBytes(UTF_8)));
    var underTest = new SonarCloudCdnPlugins(helper);

    underTest.getPlugin("pluginKey", "hash", stream -> assertThat(stream).hasContent("content"), cancelMonitor);
  }

  @Test
  void should_build_sonarcloud_plugin_download_urls() {
    assertThat(SonarCloudCdnPlugins.buildDownloadUrl("https://sonarcloud.io", "java", "de5308f43260d357acc97712ce4c5475"))
      .isEqualTo("https://scanner.sonarcloud.io/plugins/java/versions/de5308f43260d357acc97712ce4c5475.jar");
    assertThat(SonarCloudCdnPlugins.buildDownloadUrl("https://sonarqube.us/", "java", "de5308f43260d357acc97712ce4c5475"))
      .isEqualTo("https://scanner.sonarqube.us/plugins/java/versions/de5308f43260d357acc97712ce4c5475.jar");
    assertThat(SonarCloudCdnPlugins.buildDownloadUrl("https://sonarcloud.io", "java plugin", "plugin hash"))
      .isEqualTo("https://scanner.sonarcloud.io/plugins/java%20plugin/versions/plugin%20hash.jar");
  }

  @Test
  void should_reject_sonarcloud_url_without_host() {
    assertThatThrownBy(() -> SonarCloudCdnPlugins.buildDownloadUrl("sonarcloud.io", "java", "hash"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("SonarQube Cloud URL must contain a host: sonarcloud.io");
  }

  @Test
  void should_wrap_error_when_sonarcloud_plugin_download_url_cannot_be_built() {
    assertThatThrownBy(() -> SonarCloudCdnPlugins.buildDownloadUrl("https://[::1]", "java", "hash"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Unable to build the SonarQube Cloud plugin download URL")
      .hasCauseInstanceOf(URISyntaxException.class);
  }

}
