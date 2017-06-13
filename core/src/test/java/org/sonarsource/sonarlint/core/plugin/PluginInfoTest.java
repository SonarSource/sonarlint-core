/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.plugin;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.ZipUtils;

import static com.google.common.collect.Ordering.natural;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class PluginInfoTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void test_RequiredPlugin() throws Exception {
    PluginInfo.RequiredPlugin plugin = PluginInfo.RequiredPlugin.parse("java:1.1");
    assertThat(plugin.getKey()).isEqualTo("java");
    assertThat(plugin.getMinimalVersion().getName()).isEqualTo("1.1");
    assertThat(plugin.toString()).isEqualTo("java:1.1");
    assertThat(plugin.equals(PluginInfo.RequiredPlugin.parse("java:1.2"))).isTrue();
    assertThat(plugin.equals(PluginInfo.RequiredPlugin.parse("php:1.2"))).isFalse();

    try {
      PluginInfo.RequiredPlugin.parse("java");
      fail();
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }

  @Test
  public void test_comparison() {
    PluginInfo java1 = new PluginInfo("java").setVersion(Version.create("1.0"));
    PluginInfo java2 = new PluginInfo("java").setVersion(Version.create("2.0"));
    PluginInfo javaNoVersion = new PluginInfo("java");
    PluginInfo cobol = new PluginInfo("cobol").setVersion(Version.create("1.0"));
    PluginInfo noVersion = new PluginInfo("noVersion");
    List<PluginInfo> plugins = Arrays.asList(java1, cobol, javaNoVersion, noVersion, java2);

    List<PluginInfo> ordered = natural().sortedCopy(plugins);
    Assertions.assertThat(ordered.get(0)).isSameAs(cobol);
    Assertions.assertThat(ordered.get(1)).isSameAs(javaNoVersion);
    Assertions.assertThat(ordered.get(2)).isSameAs(java1);
    Assertions.assertThat(ordered.get(3)).isSameAs(java2);
    Assertions.assertThat(ordered.get(4)).isSameAs(noVersion);
  }

  @Test
  public void test_equals() {
    PluginInfo java1 = new PluginInfo("java").setVersion(Version.create("1.0"));
    PluginInfo java2 = new PluginInfo("java").setVersion(Version.create("2.0"));
    PluginInfo javaNoVersion = new PluginInfo("java");
    PluginInfo cobol = new PluginInfo("cobol").setVersion(Version.create("1.0"));

    assertThat(java1.equals(java1)).isTrue();
    assertThat(java1.equals(java2)).isFalse();
    assertThat(java1.equals(javaNoVersion)).isFalse();
    assertThat(java1.equals(cobol)).isFalse();
    assertThat(java1.equals("java:1.0")).isFalse();
    assertThat(java1.equals(null)).isFalse();
    assertThat(javaNoVersion.equals(javaNoVersion)).isTrue();

    assertThat(java1.hashCode()).isEqualTo(java1.hashCode());
    assertThat(javaNoVersion.hashCode()).isEqualTo(javaNoVersion.hashCode());
  }

  @Test
  public void test_compatibility_with_sq_version() throws IOException {
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
    assertThat(withMinSqVersion("2.0.1").isCompatibleWith("2.0.0")).isFalse();
    assertThat(withMinSqVersion("2.10").isCompatibleWith("2.1")).isFalse();
    assertThat(withMinSqVersion("10.10").isCompatibleWith("2.2")).isFalse();

    assertThat(withMinSqVersion("1.1-SNAPSHOT").isCompatibleWith("1.0")).isFalse();
    assertThat(withMinSqVersion("1.1-SNAPSHOT").isCompatibleWith("1.1")).isTrue();
    assertThat(withMinSqVersion("1.1-SNAPSHOT").isCompatibleWith("1.2")).isTrue();
    assertThat(withMinSqVersion("1.0.1-SNAPSHOT").isCompatibleWith("1.0")).isFalse();

    assertThat(withMinSqVersion("3.1-RC2").isCompatibleWith("3.2-SNAPSHOT")).isTrue();
    assertThat(withMinSqVersion("3.1-RC1").isCompatibleWith("3.2-RC2")).isTrue();
    assertThat(withMinSqVersion("3.1-RC1").isCompatibleWith("3.1-RC2")).isTrue();

    assertThat(withMinSqVersion(null).isCompatibleWith("0")).isTrue();
    assertThat(withMinSqVersion(null).isCompatibleWith("3.1")).isTrue();
  }

  @Test
  public void create_from_minimal_manifest() throws Exception {
    PluginManifest manifest = new PluginManifest();
    manifest.setKey("java");
    manifest.setVersion("1.0");
    manifest.setName("Java");
    manifest.setMainClass("org.foo.FooPlugin");

    Path jarFile = temp.newFile().toPath();
    PluginInfo pluginInfo = PluginInfo.create(jarFile, manifest);

    assertThat(pluginInfo.getKey()).isEqualTo("java");
    assertThat(pluginInfo.getName()).isEqualTo("Java");
    assertThat(pluginInfo.getVersion().getName()).isEqualTo("1.0");
    assertThat(pluginInfo.getJarFile()).isEqualTo(jarFile.toFile());
    assertThat(pluginInfo.getMainClass()).isEqualTo("org.foo.FooPlugin");
    assertThat(pluginInfo.getBasePlugin()).isNull();
    assertThat(pluginInfo.getImplementationBuild()).isNull();
    assertThat(pluginInfo.getMinimalSqVersion()).isNull();
    assertThat(pluginInfo.getRequiredPlugins()).isEmpty();
  }

  @Test
  public void create_from_complete_manifest() throws Exception {
    PluginManifest manifest = new PluginManifest();
    manifest.setKey("fbcontrib");
    manifest.setVersion("2.0");
    manifest.setName("Java");
    manifest.setMainClass("org.fb.FindbugsPlugin");
    manifest.setBasePlugin("findbugs");
    manifest.setSonarVersion("4.5.1");
    manifest.setRequirePlugins(new String[] {"java:2.0", "pmd:1.3"});
    manifest.setImplementationBuild("SHA1");

    Path jarFile = temp.newFile().toPath();
    PluginInfo pluginInfo = PluginInfo.create(jarFile, manifest);

    assertThat(pluginInfo.getBasePlugin()).isEqualTo("findbugs");
    assertThat(pluginInfo.getImplementationBuild()).isEqualTo("SHA1");
    assertThat(pluginInfo.getMinimalSqVersion().getName()).isEqualTo("4.5.1");
    assertThat(pluginInfo.getRequiredPlugins()).extracting("key").containsOnly("java", "pmd");
  }

  @Test
  public void create_from_file() throws URISyntaxException {
    Path checkstyleJar = Paths.get(getClass().getResource("/sonar-checkstyle-plugin-2.8.jar").toURI());
    PluginInfo checkstyleInfo = PluginInfo.create(checkstyleJar);

    assertThat(checkstyleInfo.getName()).isEqualTo("Checkstyle");
    assertThat(checkstyleInfo.getMinimalSqVersion()).isEqualTo(Version.create("2.8"));
  }

  @Test
  public void test_toString() throws Exception {
    PluginInfo pluginInfo = new PluginInfo("java").setVersion(Version.create("1.1"));
    assertThat(pluginInfo.toString()).isEqualTo("[java / 1.1]");

    pluginInfo.setImplementationBuild("SHA1");
    assertThat(pluginInfo.toString()).isEqualTo("[java / 1.1 / SHA1]");
  }

  /**
   * The English bundle plugin was removed in 5.2. L10n plugins do not need to declare
   * it as base plugin anymore
   */
  @Test
  public void l10n_plugins_should_not_extend_english_plugin() {
    PluginInfo pluginInfo = new PluginInfo("l10nfr").setBasePlugin("l10nen");
    assertThat(pluginInfo.getBasePlugin()).isNull();
  }

  @Test
  public void fail_when_jar_is_not_a_plugin() throws IOException {
    // this JAR has a manifest but is not a plugin
    File jarRootDir = temp.newFolder();
    FileUtils.write(new File(jarRootDir, "META-INF/MANIFEST.MF"), "Build-Jdk: 1.6.0_15");
    Path jar = temp.newFile().toPath();
    ZipUtils.zipDir(jarRootDir, jar.toFile());

    expectedException.expect(MessageException.class);
    expectedException.expectMessage("File is not a plugin. Please delete it and restart: " + jar.toAbsolutePath());

    PluginInfo.create(jar);
  }

  PluginInfo withMinSqVersion(@Nullable String version) {
    PluginInfo pluginInfo = new PluginInfo("foo");
    if (version != null) {
      pluginInfo.setMinimalSqVersion(Version.create(version));
    }
    return pluginInfo;
  }
}
