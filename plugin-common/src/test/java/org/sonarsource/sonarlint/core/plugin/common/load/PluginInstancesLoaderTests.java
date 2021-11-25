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

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.Plugin;
import org.sonarsource.sonarlint.core.plugin.common.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginInstancesLoaderTests {

  PluginClassloaderFactory classloaderFactory = mock(PluginClassloaderFactory.class);
  PluginInstancesLoader loader = new PluginInstancesLoader(classloaderFactory);

  @Test
  void instantiate_plugin_entry_point() {
    PluginClassLoaderDef def = new PluginClassLoaderDef("fake");
    def.addMainClass("fake", FakePlugin.class.getName());

    Map<String, Plugin> instances = loader.instantiatePluginClasses(Map.of(def, getClass().getClassLoader()));
    assertThat(instances).containsOnlyKeys("fake");
    assertThat(instances.get("fake")).isInstanceOf(FakePlugin.class);
  }

  @Test
  void plugin_entry_point_must_be_no_arg_public() {
    PluginClassLoaderDef def = new PluginClassLoaderDef("fake");
    def.addMainClass("fake", IncorrectPlugin.class.getName());

    IllegalStateException expected = assertThrows(IllegalStateException.class, () -> loader.instantiatePluginClasses(Map.of(def, getClass().getClassLoader())));
    assertThat(expected).hasMessage("Fail to instantiate class [org.sonarsource.sonarlint.core.plugin.common.load.PluginInstancesLoaderTests$IncorrectPlugin] of plugin [fake]");
  }

  @Test
  void define_classloader(@TempDir Path tmp) throws Exception {
    Path jarFile = tmp.resolve("fakePlugin.jar");
    PluginManifest manifest = mock(PluginManifest.class);
    when(manifest.getKey()).thenReturn("foo");
    when(manifest.getMainClass()).thenReturn("org.foo.FooPlugin");
    when(manifest.getSonarMinVersion()).thenReturn(Optional.of(Version.create("5.2")));
    PluginInfo info = PluginInfo.create(jarFile, manifest);

    Collection<PluginClassLoaderDef> defs = loader.defineClassloaders(List.of(info));

    assertThat(defs).hasSize(1);
    PluginClassLoaderDef def = defs.iterator().next();
    assertThat(def.getBasePluginKey()).isEqualTo("foo");
    assertThat(def.getFiles()).containsExactly(jarFile);
    assertThat(def.getMainClassesByPluginKey()).containsOnly(MapEntry.entry("foo", "org.foo.FooPlugin"));
  }

  /**
   * A plugin (the "base" plugin) can be extended by other plugins. In this case they share the same classloader.
   */
  @Test
  void test_plugins_sharing_the_same_classloader(@TempDir Path tmp) throws Exception {
    Path baseJarFile = tmp.resolve("fakeBasePlugin.jar");
    PluginManifest manifestBase = mock(PluginManifest.class);
    when(manifestBase.getKey()).thenReturn("foo");
    when(manifestBase.getMainClass()).thenReturn("org.foo.FooPlugin");
    PluginInfo base = PluginInfo.create(baseJarFile, manifestBase);

    Path extensionJar1 = tmp.resolve("fakePlugin1.jar");
    PluginManifest manifestJar1 = mock(PluginManifest.class);
    when(manifestJar1.getKey()).thenReturn("fooExtension1");
    when(manifestJar1.getMainClass()).thenReturn("org.foo.Extension1Plugin");
    when(manifestJar1.getBasePluginKey()).thenReturn("foo");
    PluginInfo extension1 = PluginInfo.create(extensionJar1, manifestJar1);

    Path extensionJar2 = tmp.resolve("fakePlugin2.jar");
    PluginManifest manifestJar2 = mock(PluginManifest.class);
    when(manifestJar2.getKey()).thenReturn("fooExtension2");
    when(manifestJar2.getMainClass()).thenReturn("org.foo.Extension2Plugin");
    when(manifestJar2.getBasePluginKey()).thenReturn("foo");
    PluginInfo extension2 = PluginInfo.create(extensionJar2, manifestJar2);

    Collection<PluginClassLoaderDef> defs = loader.defineClassloaders(List.of(base, extension1, extension2));

    assertThat(defs).hasSize(1);
    PluginClassLoaderDef def = defs.iterator().next();
    assertThat(def.getBasePluginKey()).isEqualTo("foo");
    assertThat(def.getFiles()).containsOnly(baseJarFile, extensionJar1, extensionJar2);
    assertThat(def.getMainClassesByPluginKey()).containsOnly(
      entry("foo", "org.foo.FooPlugin"),
      entry("fooExtension1", "org.foo.Extension1Plugin"),
      entry("fooExtension2", "org.foo.Extension2Plugin"));
  }

  public static class FakePlugin implements Plugin {

    @Override
    public void define(Context context) {
    }

  }

  /**
   * No public empty-param constructor
   */
  public static class IncorrectPlugin implements Plugin {
    public IncorrectPlugin(String s) {
    }

    @Override
    public void define(Context context) {
    }
  }
}
