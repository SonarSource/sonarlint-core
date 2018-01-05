/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.client.api.common;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileExclusionsTest {
  FileExclusions fileExclusions;

  @Before
  public void before() {
    Set<String> glob = Collections.singleton("**/*.js");
    Set<String> files = Collections.singleton(new File("dir/file.java").getAbsolutePath());
    Set<String> dir = Collections.singleton("src");
    fileExclusions = new FileExclusions(files, dir, glob);
  }

  @Test
  public void should_exclude_with_glob_relative_path() {
    assertThat(fileExclusions.test(new File("dir2/file.js").getAbsolutePath())).isTrue();
    assertThat(fileExclusions.test(new File("dir2/file.java").getAbsolutePath())).isFalse();

  }

  @Test
  public void should_exclude_with_glob_absolute_path() {
    assertThat(fileExclusions.test(new File("/absolute/dir/file.js").getAbsolutePath())).isTrue();
    assertThat(fileExclusions.test(new File("/absolute/dir/file.java").getAbsolutePath())).isFalse();
  }

  @Test
  public void should_exclude_with_file() {
    assertThat(fileExclusions.test(new File("dir/file2.java").getAbsolutePath())).isFalse();
    assertThat(fileExclusions.test(new File("dir/file.java").getAbsolutePath())).isTrue();
  }

  @Test
  public void should_exclude_with_dir() {
    assertThat(fileExclusions.test(new File("dir/class2.java").getAbsolutePath())).isFalse();
    assertThat(fileExclusions.test("src/class.java")).isTrue();
  }
}
