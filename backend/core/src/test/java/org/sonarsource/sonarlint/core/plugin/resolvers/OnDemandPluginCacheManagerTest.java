/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.plugin.resolvers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;

class OnDemandPluginCacheManagerTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  private final OnDemandPluginCacheManager underTest = new OnDemandPluginCacheManager();

  @Test
  void should_do_nothing_when_cache_directory_does_not_exist() {
    var missing = tempDir.resolve("does-not-exist");

    Assertions.assertDoesNotThrow(() -> underTest.cleanupOldVersions(missing, "1.0"));
  }

  @Test
  void should_not_delete_current_version_directory() throws IOException {
    var cacheDir = tempDir.resolve("cpp");
    var currentVersionDir = cacheDir.resolve("1.0");
    Files.createDirectories(currentVersionDir);
    setOldModificationTime(currentVersionDir);

    underTest.cleanupOldVersions(cacheDir, "1.0");

    assertThat(currentVersionDir).exists();
  }

  @Test
  void should_delete_old_version_directory() throws IOException {
    var cacheDir = tempDir.resolve("cpp");
    var oldVersionDir = cacheDir.resolve("0.9");
    Files.createDirectories(oldVersionDir);
    setOldModificationTime(oldVersionDir);

    underTest.cleanupOldVersions(cacheDir, "1.0");

    assertThat(oldVersionDir).doesNotExist();
  }

  @Test
  void should_not_delete_recently_modified_version_directory() throws IOException {
    var cacheDir = tempDir.resolve("cpp");
    var recentVersionDir = cacheDir.resolve("0.9");
    Files.createDirectories(recentVersionDir);
    // Modification time is "now" by default — well within retention period

    underTest.cleanupOldVersions(cacheDir, "1.0");

    assertThat(recentVersionDir).exists();
  }

  @Test
  void should_delete_only_old_version_directories_in_mixed_scenario() throws IOException {
    var cacheDir = tempDir.resolve("cpp");
    var currentDir = cacheDir.resolve("1.0");
    var oldDir = cacheDir.resolve("0.8");
    var recentDir = cacheDir.resolve("0.9");
    Files.createDirectories(currentDir);
    Files.createDirectories(oldDir);
    Files.createDirectories(recentDir);
    setOldModificationTime(currentDir); // current is old but should be kept by name
    setOldModificationTime(oldDir);
    // recentDir stays with current modification time

    underTest.cleanupOldVersions(cacheDir, "1.0");

    assertThat(currentDir).exists();
    assertThat(oldDir).doesNotExist();
    assertThat(recentDir).exists();
  }

  @Test
  void should_do_nothing_when_cache_directory_is_empty() throws IOException {
    var cacheDir = tempDir.resolve("cpp");
    Files.createDirectories(cacheDir);

    underTest.cleanupOldVersions(cacheDir, "1.0");

    assertThat(cacheDir).exists();
  }

  private static void setOldModificationTime(Path path) throws IOException {
    var oldTime = Instant.now().minus(61, ChronoUnit.DAYS);
    Files.setLastModifiedTime(path, FileTime.from(oldTime));
  }

}
