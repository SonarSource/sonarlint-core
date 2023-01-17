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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.internal.apachecommons.io.FileUtils;
import org.sonar.api.utils.ZipUtils;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.plugin.commons.loading.SonarPluginManifest.RequiredPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginInfoTests {

  @Test
  void test_equals() {
    var java1 = new PluginInfo("java").setVersion(Version.create("1.0"));
    var java2 = new PluginInfo("java").setVersion(Version.create("2.0"));
    var javaNoVersion = new PluginInfo("java");
    var cobol = new PluginInfo("cobol").setVersion(Version.create("1.0"));

    assertThat(java1.equals(java1)).isTrue();
    assertThat(java1.equals(java2)).isFalse();
    assertThat(java1.equals(javaNoVersion)).isFalse();
    assertThat(java1.equals(cobol)).isFalse();
    assertThat(java1.equals("java:1.0")).isFalse();
    assertThat(java1.equals(null)).isFalse();
    assertThat(javaNoVersion.equals(javaNoVersion)).isTrue();

    assertThat(java1).hasSameHashCodeAs(java1);
    assertThat(javaNoVersion).hasSameHashCodeAs(javaNoVersion);
  }

  @Test
  void test_compatibility_with_sq_version() throws IOException {
    assertThat(withMinSqVersion("1.1").isCompatibleWith("1.1")).isTrue();
    assertThat(withMinSqVersion("1.1").isCompatibleWith("1.1.0")).isTrue();
    assertThat(withMinSqVersion("1.0").isCompatibleWith("1.0.0")).isTrue();

    assertThat(withMinSqVersion("1.0").isCompatibleWith("1.1")).isTrue();
    assertThat(withMinSqVersion("1.1.1").isCompatibleWith("1.1.2")).isTrue();
    assertThat(withMinSqVersion("2.0").isCompatibleWith("2.1.0")).isTrue();
    assertThat(withMinSqVersion("3.2").isCompatibleWith("3.2-RC1")).isTrue();
    assertThat(withMinSqVersion("3.2").isCompatibleWith("3.2-RC2")).isTrue();
    assertThat(withMinSqVersion("3.2").isCompatibleWith("3.1-RC2")).isFalse();

    assertThat(withMinSqVersion("1.1").isCompatibleWith("1.0")).isFalse();
    assertThat(withMinSqVersion("2.0.1").isCompatibleWith("2.0.0")).isTrue();
    assertThat(withMinSqVersion("2.10").isCompatibleWith("2.1")).isFalse();
    assertThat(withMinSqVersion("10.10").isCompatibleWith("2.2")).isFalse();

    assertThat(withMinSqVersion("1.1-SNAPSHOT").isCompatibleWith("1.0")).isFalse();
    assertThat(withMinSqVersion("1.1-SNAPSHOT").isCompatibleWith("1.1")).isTrue();
    assertThat(withMinSqVersion("1.1-SNAPSHOT").isCompatibleWith("1.2")).isTrue();
    assertThat(withMinSqVersion("1.0.1-SNAPSHOT").isCompatibleWith("1.0")).isTrue();

    assertThat(withMinSqVersion("3.1-RC2").isCompatibleWith("3.2-SNAPSHOT")).isTrue();
    assertThat(withMinSqVersion("3.1-RC1").isCompatibleWith("3.2-RC2")).isTrue();
    assertThat(withMinSqVersion("3.1-RC1").isCompatibleWith("3.1-RC2")).isTrue();

    assertThat(withMinSqVersion(null).isCompatibleWith("0")).isTrue();
    assertThat(withMinSqVersion(null).isCompatibleWith("3.1")).isTrue();

    assertThat(withMinSqVersion("7.0.0.12345").isCompatibleWith("7.0")).isTrue();
  }

  @Test
  void create_from_minimal_manifest(@TempDir Path temp) throws Exception {
    var manifest = mock(SonarPluginManifest.class);
    when(manifest.getKey()).thenReturn("java");
    when(manifest.getVersion()).thenReturn("1.0");
    when(manifest.getName()).thenReturn("Java");
    when(manifest.getMainClass()).thenReturn("org.foo.FooPlugin");

    var jarFile = temp.resolve("myPlugin.jar");
    var pluginInfo = PluginInfo.create(jarFile, manifest);

    assertThat(pluginInfo.getKey()).isEqualTo("java");
    assertThat(pluginInfo.getName()).isEqualTo("Java");
    assertThat(pluginInfo.getVersion().getName()).isEqualTo("1.0");
    assertThat(pluginInfo.getJarFile()).isEqualTo(jarFile.toFile());
    assertThat(pluginInfo.getMainClass()).isEqualTo("org.foo.FooPlugin");
    assertThat(pluginInfo.getBasePlugin()).isNull();
    assertThat(pluginInfo.getMinimalSqVersion()).isNull();
    assertThat(pluginInfo.getRequiredPlugins()).isEmpty();
    assertThat(pluginInfo.getJreMinVersion()).isNull();
    assertThat(pluginInfo.getNodeJsMinVersion()).isNull();
  }

  @Test
  void create_from_complete_manifest(@TempDir Path temp) throws Exception {
    var manifest = mock(SonarPluginManifest.class);
    when(manifest.getKey()).thenReturn("fbcontrib");
    when(manifest.getVersion()).thenReturn("2.0");
    when(manifest.getName()).thenReturn("Java");
    when(manifest.getMainClass()).thenReturn("org.fb.FindbugsPlugin");
    when(manifest.getBasePluginKey()).thenReturn("findbugs");
    when(manifest.getSonarMinVersion()).thenReturn(Optional.of(Version.create("4.5.1")));
    when(manifest.getRequiredPlugins()).thenReturn(List.of(new RequiredPlugin("java", Version.create("2.0")), new RequiredPlugin("pmd", Version.create("1.3"))));
    when(manifest.getJreMinVersion()).thenReturn(Optional.of(Version.create("11")));
    when(manifest.getNodeJsMinVersion()).thenReturn(Optional.of(Version.create("12.18.3")));

    var jarFile = temp.resolve("myPlugin.jar");
    var pluginInfo = PluginInfo.create(jarFile, manifest);

    assertThat(pluginInfo.getBasePlugin()).isEqualTo("findbugs");
    assertThat(pluginInfo.getMinimalSqVersion().getName()).isEqualTo("4.5.1");
    assertThat(pluginInfo.getRequiredPlugins()).extracting("key").containsOnly("java", "pmd");
    assertThat(pluginInfo.getJreMinVersion().getName()).isEqualTo("11");
    assertThat(pluginInfo.getNodeJsMinVersion().getName()).isEqualTo("12.18.3");
  }

  @Test
  void create_from_file() throws URISyntaxException {
    var checkstyleJar = Paths.get(getClass().getResource("/sonar-checkstyle-plugin-2.8.jar").toURI());
    var checkstyleInfo = PluginInfo.create(checkstyleJar);

    assertThat(checkstyleInfo.getName()).isEqualTo("Checkstyle");
    assertThat(checkstyleInfo.getMinimalSqVersion()).isEqualTo(Version.create("2.8"));
  }

  @Test
  void test_toString() throws Exception {
    var pluginInfo = new PluginInfo("java").setVersion(Version.create("1.1"));
    assertThat(pluginInfo).hasToString("[java / 1.1]");
  }

  @Test
  void fail_when_jar_is_not_a_plugin(@TempDir Path temp) throws IOException {
    // this JAR has a manifest but is not a plugin
    var jarRootDir = Files.createTempDirectory(temp, "myPlugin").toFile();
    FileUtils.write(new File(jarRootDir, "META-INF/MANIFEST.MF"), "Build-Jdk: 1.6.0_15", StandardCharsets.UTF_8);
    var jar = temp.resolve("myPlugin.jar");
    ZipUtils.zipDir(jarRootDir, jar.toFile());

    var thrown = assertThrows(IllegalStateException.class, () -> PluginInfo.create(jar));
    assertThat(thrown).hasMessage("Error while reading plugin manifest from jar: " + jar.toAbsolutePath());
  }

  PluginInfo withMinSqVersion(@Nullable String version) {
    var pluginInfo = new PluginInfo("foo");
    if (version != null) {
      pluginInfo.setMinimalSqVersion(Version.create(version));
    }
    return pluginInfo;
  }
}
