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
package org.sonarsource.sonarlint.core.plugin.commons.loading;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.plugin.commons.loading.SonarPluginManifest.RequiredPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SonarPluginManifestTests {

  @Test
  void test_RequiredPlugin() throws Exception {
    var plugin = SonarPluginManifest.RequiredPlugin.parse("java:1.1");
    assertThat(plugin.getKey()).isEqualTo("java");
    assertThat(plugin.getMinimalVersion().getName()).isEqualTo("1.1");

    assertThrows(IllegalArgumentException.class, () -> SonarPluginManifest.RequiredPlugin.parse("java"));
  }

  @Test
  void test() {
    var fake = Paths.get("fake.jar");
    assertThrows(RuntimeException.class, () -> SonarPluginManifest.fromJar(fake));
  }

  @Test
  void should_create_manifest_from_jar() throws URISyntaxException, IOException {
    var checkstyleJar = Paths.get(getClass().getResource("/sonar-checkstyle-plugin-2.8.jar").toURI());
    var manifest = SonarPluginManifest.fromJar(checkstyleJar);

    assertThat(manifest.getKey()).isEqualTo("checkstyle");
    assertThat(manifest.getName()).isEqualTo("Checkstyle");
    assertThat(manifest.getRequiredPlugins()).isEmpty();
    assertThat(manifest.getMainClass()).isEqualTo("org.sonar.plugins.checkstyle.CheckstylePlugin");
    assertThat(manifest.getVersion().length()).isGreaterThan(1);
    assertThat(manifest.getJreMinVersion()).isEmpty();
    assertThat(manifest.getNodeJsMinVersion()).isEmpty();
  }

  @Test
  void should_add_requires_plugins() throws URISyntaxException, IOException {
    var jar = getClass().getResource("/SonarPluginManifestTests/plugin-with-require-plugins.jar");

    var manifest = SonarPluginManifest.fromJar(Paths.get(jar.toURI()));

    assertThat(manifest.getRequiredPlugins())
      .usingRecursiveFieldByFieldElementComparator()
      .containsExactlyInAnyOrder(new RequiredPlugin("scm", Version.create("1.0")), new RequiredPlugin("fake", Version.create("1.1")));
  }

  @Test
  void should_parse_jre_min_version() throws URISyntaxException, IOException {
    var jar = getClass().getResource("/SonarPluginManifestTests/plugin-with-jre-min.jar");

    var manifest = SonarPluginManifest.fromJar(Paths.get(jar.toURI()));

    assertThat(manifest.getJreMinVersion()).contains(Version.create("11"));
  }

  @Test
  void should_default_jre_min_version_to_null() throws URISyntaxException, IOException {
    var jar = getClass().getResource("/SonarPluginManifestTests/plugin-without-jre-min.jar");

    var manifest = SonarPluginManifest.fromJar(Paths.get(jar.toURI()));

    assertThat(manifest.getJreMinVersion()).isEmpty();
  }

  @Test
  void should_parse_nodejs_min_version() throws URISyntaxException, IOException {
    var jar = getClass().getResource("/SonarPluginManifestTests/plugin-with-nodejs-min.jar");

    var manifest = SonarPluginManifest.fromJar(Paths.get(jar.toURI()));

    assertThat(manifest.getNodeJsMinVersion()).contains(Version.create("12.18.3"));
  }
}
