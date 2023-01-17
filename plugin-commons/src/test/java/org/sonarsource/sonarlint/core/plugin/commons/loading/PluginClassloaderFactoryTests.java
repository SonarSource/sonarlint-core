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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileListener;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class PluginClassloaderFactoryTests {

  private static final String BASE_PLUGIN_CLASSNAME = "org.sonar.plugins.base.BasePlugin";
  private static final String DEPENDENT_PLUGIN_CLASSNAME = "org.sonar.plugins.dependent.DependentPlugin";
  private static final String BASE_PLUGIN_KEY = "base";
  private static final String DEPENDENT_PLUGIN_KEY = "dependent";

  private final PluginClassloaderFactory factory = new PluginClassloaderFactory();

  @Test
  void create_isolated_classloader() {
    var def = basePluginDef();
    var map = factory.create(getClass().getClassLoader(), List.of(def));

    assertThat(map).containsOnlyKeys(def);
    var classLoader = map.get(def);

    // plugin can access to sonar-plugin-api classes...
    assertThat(canLoadClass(classLoader, RulesDefinition.class.getCanonicalName())).isTrue();
    // ... to sonarlint-plugin-api classes...
    assertThat(canLoadClass(classLoader, ModuleFileListener.class.getCanonicalName())).isTrue();
    // ... and of course to its own classes !
    assertThat(canLoadClass(classLoader, BASE_PLUGIN_CLASSNAME)).isTrue();

    // plugin can not access to core classes
    assertThat(canLoadClass(classLoader, PluginClassloaderFactory.class.getCanonicalName())).isFalse();
    assertThat(canLoadClass(classLoader, Test.class.getCanonicalName())).isFalse();
    assertThat(canLoadClass(classLoader, StringUtils.class.getCanonicalName())).isFalse();
  }

  @Test
  void classloader_exports_resources_to_other_classloaders() {
    var baseDef = basePluginDef();
    var dependentDef = dependentPluginDef();
    var map = factory.create(getClass().getClassLoader(), asList(baseDef, dependentDef));
    var baseClassloader = map.get(baseDef);
    var dependentClassloader = map.get(dependentDef);

    // base-plugin exports its API package to other plugins
    assertThat(canLoadClass(dependentClassloader, "org.sonar.plugins.base.api.BaseApi")).isTrue();
    assertThat(canLoadClass(dependentClassloader, BASE_PLUGIN_CLASSNAME)).isFalse();
    assertThat(canLoadClass(dependentClassloader, DEPENDENT_PLUGIN_CLASSNAME)).isTrue();

    // dependent-plugin does not export its classes
    assertThat(canLoadClass(baseClassloader, DEPENDENT_PLUGIN_CLASSNAME)).isFalse();
    assertThat(canLoadClass(baseClassloader, BASE_PLUGIN_CLASSNAME)).isTrue();
  }

  private static PluginClassLoaderDef basePluginDef() {
    var def = new PluginClassLoaderDef(BASE_PLUGIN_KEY);
    def.addMainClass(BASE_PLUGIN_KEY, BASE_PLUGIN_CLASSNAME);
    def.getExportMask().addInclusion("org/sonar/plugins/base/api/");
    def.addFiles(List.of(fakePluginJar("base-plugin/target/base-plugin-0.1-SNAPSHOT.jar")));
    return def;
  }

  private static PluginClassLoaderDef dependentPluginDef() {
    var def = new PluginClassLoaderDef(DEPENDENT_PLUGIN_KEY);
    def.addMainClass(DEPENDENT_PLUGIN_KEY, DEPENDENT_PLUGIN_CLASSNAME);
    def.getExportMask().addInclusion("org/sonar/plugins/dependent/api/");
    def.addFiles(List.of(fakePluginJar("dependent-plugin/target/dependent-plugin-0.1-SNAPSHOT.jar")));
    return def;
  }

  private static File fakePluginJar(String path) {
    // Maven way
    var file = Paths.get("src/test/projects/" + path);
    if (!Files.exists(file)) {
      // Intellij way
      file = Paths.get("sonar-core/src/test/projects/" + path);
      if (!Files.exists(file)) {
        throw new IllegalArgumentException("Fake projects are not built: " + path);
      }
    }
    return file.toFile();
  }

  private static boolean canLoadClass(ClassLoader classloader, String classname) {
    try {
      classloader.loadClass(classname);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
