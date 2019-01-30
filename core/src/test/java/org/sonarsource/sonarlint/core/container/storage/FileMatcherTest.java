/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.storage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.sonarsource.sonarlint.core.util.ReversePathTree;

import static org.assertj.core.api.Assertions.assertThat;

public class FileMatcherTest {
  private FileMatcher fileMatcher = new FileMatcher(new ReversePathTree());

  @Test
  public void simple_case_without_prefixes() {
    List<Path> paths = Collections.singletonList(Paths.get("project1/src/main/java/File.java"));
    FileMatcher.Result match = fileMatcher.match(paths, paths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get(""));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get(""));
  }

  @Test
  public void simple_case_with_prefixes() {
    List<Path> idePaths = Collections.singletonList(Paths.get("local/src/main/java/File.java"));
    List<Path> sqPaths = Collections.singletonList(Paths.get("sq/src/main/java/File.java"));
    FileMatcher.Result match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local"));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get("sq"));
  }

  @Test
  public void no_match() {
    List<Path> idePaths = Collections.singletonList(Paths.get("local/src/main/java/File1.java"));
    List<Path> sqPaths = Collections.singletonList(Paths.get("sq/src/main/java/File2.java"));
    FileMatcher.Result match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get(""));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get(""));
  }

  @Test
  public void empty_project_in_ide() {
    List<Path> idePaths = Collections.emptyList();
    List<Path> sqPaths = Collections.singletonList(Paths.get("sq/src/main/java/File2.java"));
    FileMatcher.Result match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get(""));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get(""));
  }

  @Test
  public void should_return_shortest_prefix_if_there_are_ties() {
    List<Path> idePaths = Arrays.asList(
      Paths.get("pom.xml")
    );

    List<Path> sqPaths = Arrays.asList(
      Paths.get("aq1/module2/pom.xml"),
      Paths.get("aq2/pom.xml"),
      Paths.get("pom.xml"),
      Paths.get("aq1/module1/pom.xml")
    );
    FileMatcher.Result match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get(""));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get(""));
  }

  @Test
  public void should_return_most_common_prefixes() {
    List<Path> idePaths = Arrays.asList(
      Paths.get("local1/src/main/java/A.java"),
      Paths.get("local1/src/main/java/B.java"),
      Paths.get("local2/src/main/java/B.java")
    );

    List<Path> sqPaths = Arrays.asList(
      Paths.get("sq1/src/main/java/A.java"),
      Paths.get("sq2/src/main/java/A.java"),
      Paths.get("sq1/src/main/java/B.java")

    );
    FileMatcher.Result match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local1"));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get("sq1"));
  }

  @Test
  public void verify_equals_and_hashcode_of_result() {
    FileMatcher.Result r1 = new FileMatcher.Result(Paths.get("ide1"), Paths.get("sq1"));
    FileMatcher.Result r2 = new FileMatcher.Result(Paths.get("ide2"), Paths.get("sq1"));
    FileMatcher.Result r3 = new FileMatcher.Result(Paths.get("ide1"), Paths.get("sq2"));
    FileMatcher.Result r4 = new FileMatcher.Result(Paths.get("ide1"), Paths.get("sq1"));

    assertThat(r1.equals(r1)).isTrue();
    assertThat(r1.equals(r4)).isTrue();
    assertThat(r1.hashCode()).isEqualTo(r4.hashCode());

    assertThat(r1.equals(r3)).isFalse();
    assertThat(r3.equals(r2)).isFalse();
    assertThat(r1.equals(new Object())).isFalse();
    assertThat(r1.equals(null)).isFalse();
  }
}
