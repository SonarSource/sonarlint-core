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

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.text.ParseException;
import org.junit.Test;
import org.sonarsource.sonarlint.core.util.PluginLocator;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginManifestTest {

  @Test(expected = RuntimeException.class)
  public void test() throws Exception {
    new PluginManifest(Paths.get("fake.jar"));
  }

  @Test
  public void should_create_manifest() throws URISyntaxException, IOException {
    PluginManifest manifest = new PluginManifest(Paths.get(PluginLocator.getJavaPluginUrl().toURI()));

    assertThat(manifest.getKey()).isEqualTo("java");
    assertThat(manifest.getName()).isEqualTo("SonarJava");
    assertThat(manifest.getRequirePlugins()).isEmpty();
    assertThat(manifest.getMainClass()).isEqualTo("org.sonar.plugins.java.JavaPlugin");
    assertThat(manifest.getVersion().length()).isGreaterThan(1);
    assertThat(manifest.isUseChildFirstClassLoader()).isFalse();
    assertThat(manifest.getDependencies()).contains("META-INF/lib/org.jacoco.core-0.7.5.201505241946.jar");
    assertThat(manifest.getImplementationBuild()).isNotEmpty();
  }

  @Test
  public void accessors() throws URISyntaxException, IOException, ParseException {
    URL jar = PluginLocator.getJavaPluginUrl();

    PluginManifest manifest = new PluginManifest(Paths.get(jar.toURI()));

    manifest.setName("newName");
    String[] requirePlugins = new String[2];
    requirePlugins[0] = "requiredPlugin1";
    requirePlugins[1] = "requiredPlugin2";
    manifest.setRequirePlugins(requirePlugins);
    manifest.setSonarVersion("newSonarVersion");
    manifest.setMainClass("newMainClass");
    manifest.setUseChildFirstClassLoader(false);
    manifest.setBasePlugin("newBasePlugin");
    manifest.setImplementationBuild("newImplementationBuild");

    assertThat(manifest.getName()).isEqualTo("newName");
    assertThat(manifest.getRequirePlugins()).hasSize(2);
    assertThat(manifest.getSonarVersion()).isEqualTo("newSonarVersion");
    assertThat(manifest.getMainClass()).isEqualTo("newMainClass");
    assertThat(manifest.isUseChildFirstClassLoader()).isFalse();
    assertThat(manifest.getBasePlugin()).isEqualTo("newBasePlugin");
    assertThat(manifest.getImplementationBuild()).isEqualTo("newImplementationBuild");
  }

  @Test
  public void should_add_requires_plugins() throws URISyntaxException, IOException {
    URL jar = getClass().getResource("/plugin-with-require-plugins.jar");

    PluginManifest manifest = new PluginManifest(Paths.get(jar.toURI()));

    assertThat(manifest.getRequirePlugins()).hasSize(2);
    assertThat(manifest.getRequirePlugins()[0]).isEqualTo("scm:1.0");
    assertThat(manifest.getRequirePlugins()[1]).isEqualTo("fake:1.1");
  }
}
