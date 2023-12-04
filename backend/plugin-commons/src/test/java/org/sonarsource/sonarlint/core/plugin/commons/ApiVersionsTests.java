/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons;

import java.net.URL;
import org.junit.jupiter.api.Test;
import org.sonar.api.utils.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ApiVersionsTests {
  @Test
  void can_load_sonar_plugin_api_version_from_embedded_resource() {
    var version = ApiVersions.loadSonarPluginApiVersion();

    assertThat(version).isNotNull();
    assertThat(version.isGreaterThanOrEqual(Version.create(8, 5))).isTrue();
  }

  @Test
  void can_load_sonarlint_plugin_api_version_from_embedded_resource() {
    var version = ApiVersions.loadSonarLintPluginApiVersion();

    assertThat(version).isNotNull();
    assertThat(version.isGreaterThanOrEqual(Version.create(5, 4))).isTrue();
  }

  @Test
  void should_throw_an_exception_if_resource_does_not_exist() {
    var throwable = catchThrowable(() -> ApiVersions.loadVersion(null, "wrongPath"));

    assertThat(throwable).hasMessage("Can not load wrongPath from classpath");
  }

  @Test
  void should_throw_an_exception_if_resource_can_not_be_loaded() {
    var throwable = catchThrowable(() -> ApiVersions.loadVersion(new URL("file://wrong"), "wrongPath"));

    assertThat(throwable).hasMessage("Can not load wrongPath from classpath");
  }

}
