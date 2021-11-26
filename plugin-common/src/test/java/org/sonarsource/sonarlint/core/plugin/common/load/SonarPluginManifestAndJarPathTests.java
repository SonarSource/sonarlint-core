/*
 * SonarLint Core - Plugin Common
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
package org.sonarsource.sonarlint.core.plugin.common.load;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.internal.apachecommons.io.FileUtils;
import org.sonar.api.utils.ZipUtils;
import org.sonarsource.sonarlint.core.plugin.common.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarPluginManifestAndJarPathTests {

  @Test
  void create_from_manifest() throws Exception {
    SonarPluginManifest manifest = mock(SonarPluginManifest.class);
    when(manifest.getKey()).thenReturn("java");
    when(manifest.getName()).thenReturn("Java");

    SonarPluginManifestAndJarPath pluginInfo = new SonarPluginManifestAndJarPath(Paths.get("foo"), manifest);

    assertThat(pluginInfo.getKey()).isEqualTo("java");
    assertThat(pluginInfo.getName()).isEqualTo("Java");
    assertThat(pluginInfo.getJarPath()).isEqualTo(Paths.get("foo"));
  }

  @Test
  void create_from_file() throws URISyntaxException {
    Path checkstyleJar = Paths.get(getClass().getResource("/sonar-checkstyle-plugin-2.8.jar").toURI());
    SonarPluginManifestAndJarPath checkstyleInfo = SonarPluginManifestAndJarPath.create(checkstyleJar);

    assertThat(checkstyleInfo.getName()).isEqualTo("Checkstyle");
    assertThat(checkstyleInfo.getManifest().getSonarMinVersion()).contains(Version.create("2.8"));
  }

  @Test
  void fail_when_jar_is_not_a_plugin(@TempDir Path temp) throws IOException {
    // this JAR has a manifest but is not a plugin
    File jarRootDir = temp.resolve("jarRoot").toFile();
    FileUtils.write(new File(jarRootDir, "META-INF/MANIFEST.MF"), "Build-Jdk: 1.6.0_15", StandardCharsets.UTF_8);
    Path jar = temp.resolve("myjar.jar");
    ZipUtils.zipDir(jarRootDir, jar.toFile());

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> SonarPluginManifestAndJarPath.create(jar));
    assertThat(thrown).hasMessageContaining("Error while reading plugin manifest from jar:");
    assertThat(thrown.getCause()).hasMessageContaining("Plugin key is mandatory");
  }

}
