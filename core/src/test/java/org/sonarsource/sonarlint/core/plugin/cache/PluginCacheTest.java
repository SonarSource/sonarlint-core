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
package org.sonarsource.sonarlint.core.plugin.cache;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PluginCacheTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void not_in_cache() throws IOException {
    PluginCache cache = PluginCache.create(tempFolder.newFolder().toPath());
    assertThat(cache.get("sonar-foo-plugin-1.5.jar", "ABCDE")).isNull();
  }

  @Test
  public void found_in_cache() throws IOException {
    PluginCache cache = PluginCache.create(tempFolder.newFolder().toPath());

    // populate the cache. Assume that hash is correct.
    File cachedFile = new File(new File(cache.getCacheDir().toFile(), "ABCDE"), "sonar-foo-plugin-1.5.jar");
    FileUtils.write(cachedFile, "body");

    assertThat(cache.get("sonar-foo-plugin-1.5.jar", "ABCDE").toFile()).isNotNull().exists().isEqualTo(cachedFile);
  }

  @Test
  public void download_and_add_to_cache() throws IOException {
    PluginHashes hashes = mock(PluginHashes.class);
    PluginCache cache = new PluginCache(tempFolder.newFolder().toPath(), hashes);
    when(hashes.of(any(Path.class))).thenReturn("ABCDE");

    PluginCache.Copier downloader = new PluginCache.Copier() {
      public void copy(String filename, Path toFile) throws IOException {
        FileUtils.write(toFile.toFile(), "body");
      }
    };
    File cachedFile = cache.get("sonar-foo-plugin-1.5.jar", "ABCDE", downloader).toFile();
    assertThat(cachedFile).isNotNull().exists().isFile();
    assertThat(cachedFile.getName()).isEqualTo("sonar-foo-plugin-1.5.jar");
    assertThat(cachedFile.getParentFile().getParentFile()).isEqualTo(cache.getCacheDir().toFile());
    assertThat(FileUtils.readFileToString(cachedFile)).isEqualTo("body");
  }

  @Test
  public void download_corrupted_file() throws IOException {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("INVALID HASH");

    PluginHashes hashes = mock(PluginHashes.class);
    PluginCache cache = new PluginCache(tempFolder.newFolder().toPath(), hashes);
    when(hashes.of(any(Path.class))).thenReturn("VWXYZ");

    PluginCache.Copier downloader = new PluginCache.Copier() {
      public void copy(String filename, Path toFile) throws IOException {
        FileUtils.write(toFile.toFile(), "corrupted body");
      }
    };
    cache.get("sonar-foo-plugin-1.5.jar", "ABCDE", downloader);
  }

  @Test
  public void concurrent_download() throws IOException {
    PluginHashes hashes = mock(PluginHashes.class);
    when(hashes.of(any(Path.class))).thenReturn("ABCDE");
    final PluginCache cache = new PluginCache(tempFolder.newFolder().toPath(), hashes);

    PluginCache.Copier downloader = new PluginCache.Copier() {
      public void copy(String filename, Path toFile) throws IOException {
        // Emulate a concurrent download that adds file to cache before
        File cachedFile = new File(new File(cache.getCacheDir().toFile(), "ABCDE"), "sonar-foo-plugin-1.5.jar");
        FileUtils.write(cachedFile, "downloaded by other");

        FileUtils.write(toFile.toFile(), "downloaded by me");
      }
    };

    // do not fail
    File cachedFile = cache.get("sonar-foo-plugin-1.5.jar", "ABCDE", downloader).toFile();
    assertThat(cachedFile).isNotNull().exists().isFile();
    assertThat(cachedFile.getName()).isEqualTo("sonar-foo-plugin-1.5.jar");
    assertThat(cachedFile.getParentFile().getParentFile()).isEqualTo(cache.getCacheDir().toFile());
    assertThat(FileUtils.readFileToString(cachedFile)).contains("downloaded by");
  }
}
