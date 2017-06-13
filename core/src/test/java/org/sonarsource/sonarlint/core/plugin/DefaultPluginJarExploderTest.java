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
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultPluginJarExploderTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  File userHome;
  DefaultPluginJarExploder underTest;

  @Before
  public void setUp() throws IOException {
    userHome = temp.newFolder();
    PluginCache fileCache = new PluginCacheProvider().provide(StandaloneGlobalConfiguration.builder().setSonarLintUserHome(userHome.toPath()).build());
    underTest = new DefaultPluginJarExploder(fileCache);
  }

  @Test
  public void copy_and_extract_libs() throws IOException {
    Path fileFromCache = getFileFromCache("sonar-checkstyle-plugin-2.8.jar");
    ExplodedPlugin exploded = underTest.explode(PluginInfo.create(fileFromCache));

    assertThat(exploded.getKey()).isEqualTo("checkstyle");
    assertThat(exploded.getMain()).isFile().exists();
    assertThat(exploded.getLibs()).extracting("name").containsOnly("antlr-2.7.6.jar", "checkstyle-5.1.jar", "commons-cli-1.0.jar");
    assertThat(fileFromCache.resolveSibling("sonar-checkstyle-plugin-2.8.jar")).exists();
    assertThat(fileFromCache.resolveSibling("sonar-checkstyle-plugin-2.8.jar_unzip/META-INF/lib/checkstyle-5.1.jar")).exists();
  }

  @Test
  public void extract_only_libs() throws IOException {
    Path fileFromCache = getFileFromCache("sonar-checkstyle-plugin-2.8.jar");
    underTest.explode(PluginInfo.create(fileFromCache));

    assertThat(fileFromCache.resolveSibling("sonar-checkstyle-plugin-2.8.jar")).exists();
    assertThat(fileFromCache.resolveSibling("sonar-checkstyle-plugin-2.8.jar_unzip/META-INF/MANIFEST.MF")).doesNotExist();
    assertThat(fileFromCache.resolveSibling("sonar-checkstyle-plugin-2.8.jar_unzip/org/sonar/plugins/checkstyle/CheckstyleVersion.class")).doesNotExist();
  }

  Path getFileFromCache(String filename) throws IOException {
    File src = FileUtils.toFile(getClass().getResource("/" + filename));
    File destFile = new File(new File(userHome, "" + filename.hashCode()), filename);
    FileUtils.copyFile(src, destFile);
    return destFile.toPath();
  }

}
