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
package org.sonarsource.sonarlint.core.telemetry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintPathManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager.getPath;
import static org.sonarsource.sonarlint.core.telemetry.TelemetryPathManager.migrate;

public class TelemetryPathManagerTest {
  private static final String PRODUCT_KEY = "the-product";
  @Rule
  public final EnvironmentVariables env = new EnvironmentVariables();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Path oldPath;
  private Path newPath;

  @Before
  public void setUp() throws IOException {
    oldPath = temp.newFile().toPath();
    Files.write(oldPath, "old content".getBytes());

    env.set(SonarLintPathManager.SONARLINT_USER_HOME_ENV, temp.newFolder().toString());
    newPath = getPath(PRODUCT_KEY);
  }

  @Test
  public void should_get_path_under_sonarlint_home() {
    Path path = SonarLintPathManager.home().relativize(getPath("product"));
    assertThat(path.startsWith("..")).isFalse();
  }

  @Test
  public void migrate_should_not_copy_when_new_file_exists() throws IOException {
    Files.createDirectories(newPath.getParent());
    Files.write(newPath, "new usage".getBytes());
    doMigrate();
    assertThat(oldEqualsNew()).isFalse();
  }

  @Test
  public void migrate_should_not_copy_when_old_file_missing() throws IOException {
    Files.delete(oldPath);
    doMigrate();
    assertThat(oldEqualsNew()).isFalse();
  }

  @Test
  public void migrate_should_not_copy_when_cannot_create_parent_dirs() throws IOException {
    Files.createDirectories(newPath.getParent().getParent());
    Files.write(newPath.getParent(), "dummy".getBytes());

    doMigrate();
    assertThat(oldEqualsNew()).isFalse();
  }

  @Test
  public void migrate_should_copy() throws IOException {
    doMigrate();
    assertThat(oldEqualsNew()).isTrue();
  }

  private void doMigrate() {
    migrate(PRODUCT_KEY, oldPath);
  }

  private boolean oldEqualsNew() throws IOException {
    return oldPath.toFile().exists() && newPath.toFile().exists() && Arrays.equals(Files.readAllBytes(oldPath), Files.readAllBytes(newPath));
  }
}
