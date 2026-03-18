/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin.ondemand;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SystemStubsExtension.class)
class DownloadableArtifactTest {

  @SystemStub
  SystemProperties systemProperties;

  @Test
  void should_find_artifact_by_key() {
    var actual = DownloadableArtifact.byArtifactKey("cpp");

    assertThat(actual).contains(DownloadableArtifact.CFAMILY_PLUGIN);
  }

  @Test
  void should_return_empty_for_unknown_key() {
    var actual = DownloadableArtifact.byArtifactKey("unknown");

    assertThat(actual).isEmpty();
  }

  @Test
  void should_return_empty_for_null_key() {
    var actual = DownloadableArtifact.byArtifactKey(null);

    assertThat(actual).isEmpty();
  }

  @Test
  void should_return_version_from_properties() {
    var actual = DownloadableArtifact.CFAMILY_PLUGIN.version();

    assertThat(actual).isEqualTo("6.50.0");
  }

  @Test
  void should_throw_when_version_property_is_missing() {
    assertThatThrownBy(DownloadableArtifact.CSHARP_OSS::version)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("cs");
  }

  @Test
  void should_use_default_base_url() {
    var actual = DownloadableArtifact.CFAMILY_PLUGIN.urlPattern();

    assertThat(actual).isEqualTo("https://binaries.sonarsource.com/CommercialDistribution/sonar-cfamily-plugin/sonar-cfamily-plugin-%s.jar");
  }

  @Test
  void should_use_overridden_base_url_when_system_property_set() {
    systemProperties.set(DownloadableArtifact.PROPERTY_URL_PATTERN, "http://mock-server");

    var actual = DownloadableArtifact.CFAMILY_PLUGIN.urlPattern();

    assertThat(actual).isEqualTo("http://mock-server/CommercialDistribution/sonar-cfamily-plugin/sonar-cfamily-plugin-%s.jar");
  }
}
