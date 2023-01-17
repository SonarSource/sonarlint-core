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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.SonarPlugin;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;

class PluginInstancesLoaderTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  PluginClassloaderFactory classloaderFactory = mock(PluginClassloaderFactory.class);
  PluginInstancesLoader loader = new PluginInstancesLoader(classloaderFactory);

  @Test
  void instantiate_plugin_entry_point() {
    var def = new PluginClassLoaderDef("fake");
    def.addMainClass("fake", FakePlugin.class.getName());

    var instances = loader.instantiatePluginClasses(Map.of(def, getClass().getClassLoader()));
    assertThat(instances).containsOnlyKeys("fake");
    assertThat(instances.get("fake")).isInstanceOf(FakePlugin.class);
  }

  @Test
  void plugin_entry_point_must_be_no_arg_public() {
    var def = new PluginClassLoaderDef("fake");
    def.addMainClass("fake", IncorrectPlugin.class.getName());

    loader.instantiatePluginClasses(Map.of(def, getClass().getClassLoader()));

    assertThat(logTester.logs(ClientLogOutput.Level.ERROR))
      .contains("Fail to instantiate class [org.sonarsource.sonarlint.core.plugin.commons.loading.PluginInstancesLoaderTests$IncorrectPlugin] of plugin [fake]");
  }

  @Test
  void define_classloader(@TempDir Path tmp) {
    var jarFile = tmp.resolve("fakePlugin.jar").toFile();
    var info = new PluginInfo("foo")
      .setJarFile(jarFile)
      .setMainClass("org.foo.FooPlugin")
      .setMinimalSqVersion(Version.create("5.2"));

    var defs = loader.defineClassloaders(Map.of("foo", info));

    assertThat(defs).hasSize(1);
    var def = defs.iterator().next();
    assertThat(def.getBasePluginKey()).isEqualTo("foo");
    assertThat(def.getFiles()).containsExactly(jarFile);
    assertThat(def.getMainClassesByPluginKey()).containsOnly(MapEntry.entry("foo", "org.foo.FooPlugin"));
    // TODO test mask - require change in sonar-classloader
  }

  @Test
  void extract_dependencies() {
    var jarFile = getFile("sonar-checkstyle-plugin-2.8.jar");
    var info = new PluginInfo("checkstyle")
      .setJarFile(jarFile)
      .setMainClass("org.foo.FooPlugin")
      .setDependencies(List.of("META-INF/lib/commons-cli-1.0.jar", "META-INF/lib/checkstyle-5.1.jar", "META-INF/lib/antlr-2.7.6.jar"));

    var defs = loader.defineClassloaders(Map.of("checkstyle", info));

    assertThat(defs).hasSize(1);
    var def = defs.iterator().next();
    assertThat(def.getBasePluginKey()).isEqualTo("checkstyle");
    assertThat(def.getFiles()).hasSize(4);
    assertThat(def.getFiles()).extracting(File::getName, f -> {
      try {
        return DigestUtils.md5Hex(Files.readAllBytes(f.toPath()));
      } catch (IOException e) {
        return e.getMessage();
      }
    }).containsExactlyInAnyOrder(
      tuple("sonar-checkstyle-plugin-2.8.jar", "e7e5e17e5e297ac88d08122c56d72eb7"),
      tuple("commons-cli-1.0.jar", "d784fa8b6d98d27699781bd9a7cf19f0"),
      tuple("checkstyle-5.1.jar", "d784fa8b6d98d27699781bd9a7cf19f0"),
      tuple("antlr-2.7.6.jar", "d784fa8b6d98d27699781bd9a7cf19f0"));
  }

  /**
   * A plugin (the "base" plugin) can be extended by other plugins. In this case they share the same classloader.
   */
  @Test
  void test_plugins_sharing_the_same_classloader(@TempDir Path tmp) {
    var baseJarFile = tmp.resolve("fakeBasePlugin.jar").toFile();
    var extensionJar1 = tmp.resolve("fakePlugin1.jar").toFile();
    var extensionJar2 = tmp.resolve("fakePlugin2.jar").toFile();
    var base = new PluginInfo("foo")
      .setJarFile(baseJarFile)
      .setMainClass("org.foo.FooPlugin");

    var extension1 = new PluginInfo("fooExtension1")
      .setJarFile(extensionJar1)
      .setMainClass("org.foo.Extension1Plugin")
      .setBasePlugin("foo");

    var extension2 = new PluginInfo("fooExtension2")
      .setJarFile(extensionJar2)
      .setMainClass("org.foo.Extension2Plugin")
      .setBasePlugin("foo");

    var defs = loader.defineClassloaders(Map.of(
      base.getKey(), base, extension1.getKey(), extension1, extension2.getKey(), extension2));

    assertThat(defs).hasSize(1);
    var def = defs.iterator().next();
    assertThat(def.getBasePluginKey()).isEqualTo("foo");
    assertThat(def.getFiles()).containsOnly(baseJarFile, extensionJar1, extensionJar2);
    assertThat(def.getMainClassesByPluginKey()).containsOnly(
      entry("foo", "org.foo.FooPlugin"),
      entry("fooExtension1", "org.foo.Extension1Plugin"),
      entry("fooExtension2", "org.foo.Extension2Plugin"));
    // TODO test mask - require change in sonar-classloader
  }

  // SLCORE-222
  @Test
  void skip_plugins_when_base_plugin_missing(@TempDir Path tmp) {
    var extensionJar1 = tmp.resolve("fakePlugin1.jar").toFile();
    var extensionJar2 = tmp.resolve("fakePlugin2.jar").toFile();

    var extension1 = new PluginInfo("fooExtension1")
      .setJarFile(extensionJar1)
      .setMainClass("org.foo.Extension1Plugin");
    var extension2 = new PluginInfo("fooExtension2")
      .setJarFile(extensionJar2)
      .setMainClass("org.foo.Extension2Plugin")
      .setBasePlugin("foo");

    var defs = loader.defineClassloaders(Map.of(
      extension1.getKey(), extension1, extension2.getKey(), extension2));

    assertThat(defs).hasSize(1);
    var def = defs.iterator().next();
    assertThat(def.getFiles()).containsOnly(extensionJar1);
    assertThat(def.getMainClassesByPluginKey()).containsOnly(
      entry("fooExtension1", "org.foo.Extension1Plugin"));
  }

  public static class FakePlugin extends SonarPlugin {
    @Override
    public List getExtensions() {
      return Collections.emptyList();
    }
  }

  /**
   * No public empty-param constructor
   */
  public static class IncorrectPlugin extends SonarPlugin {
    public IncorrectPlugin(String s) {
    }

    @Override
    public List getExtensions() {
      return Collections.emptyList();
    }
  }

  private File getFile(String filename) {
    return FileUtils.toFile(getClass().getResource("/" + filename));
  }
}
