/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.global;

import java.net.URL;
import org.junit.jupiter.api.Test;
import org.sonar.api.utils.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class MetadataLoaderTest {
  @Test
  void can_load_sonar_plugin_api_version_from_embedded_resource() {
    Version version = MetadataLoader.loadSonarPluginApiVersion();

    assertThat(version).isNotNull();
    assertThat(version.isGreaterThanOrEqual(Version.create(8, 5))).isTrue();
  }

  @Test
  void can_load_sonarlint_plugin_api_version_from_embedded_resource() {
    Version version = MetadataLoader.loadSonarLintPluginApiVersion();

    assertThat(version).isNotNull();
    assertThat(version.isGreaterThanOrEqual(Version.create(5, 4))).isTrue();
  }

  @Test
  void should_throw_an_exception_if_resource_does_not_exist() {
    Throwable throwable = catchThrowable(() -> MetadataLoader.loadVersion(null, "wrongPath"));

    assertThat(throwable).hasMessage("Can not load wrongPath from classpath");
  }

  @Test
  void should_throw_an_exception_if_resource_can_not_be_loaded() {
    Throwable throwable = catchThrowable(() -> MetadataLoader.loadVersion(new URL("file://wrong"), "wrongPath"));

    assertThat(throwable).hasMessage("Can not load wrongPath from classpath");
  }

}
