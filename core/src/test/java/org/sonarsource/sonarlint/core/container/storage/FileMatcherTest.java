/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2020 SonarSource SA
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileMatcherTest {
  private FileMatcher fileMatcher = new FileMatcher();

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
  public void should_return_shortest_sq_prefix_if_there_are_ties() {
    List<Path> idePaths = Arrays.asList(
      Paths.get("pom.xml"));

    List<Path> sqPaths = Arrays.asList(
      Paths.get("aq1/module2/pom.xml"),
      Paths.get("aq2/pom.xml"),
      Paths.get("pom.xml"),
      Paths.get("aq1/module1/pom.xml"));
    FileMatcher.Result match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get(""));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get(""));

    sqPaths = Arrays.asList(
      Paths.get("aq1/module2/pom.xml"),
      Paths.get("aq2/pom.xml"),
      Paths.get("aq1/module1/pom.xml"));
    match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get(""));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get("aq2"));

    // In case there is also a tie on the prefix segment count, fallback on lexicographic order
    sqPaths = Arrays.asList(
      Paths.get("aq1/module2/pom.xml"),
      Paths.get("aq1/module1/pom.xml"));
    match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get(""));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get("aq1/module1"));
  }

  @Test
  public void more_complex_test_with_multiple_files() throws Exception {
    List<Path> idePaths = Arrays.asList(
      Paths.get("local/sub/index.html"),
      Paths.get("local/sub/product1/index.html"),
      Paths.get("local/sub/product2/index.html"),
      Paths.get("local/sub/product3/index.html"));
    List<Path> sqPaths = Arrays.asList(
      Paths.get("sq/index.html"),
      Paths.get("sq/news/index.html"),
      Paths.get("sq/news/product1/index.html"),
      Paths.get("sq/news/product2/index.html"),
      Paths.get("sq/news/product3/index.html"),
      Paths.get("sq/products/index.html"),
      Paths.get("sq/products/product1/index.html"),
      Paths.get("sq/products/product2/index.html"),
      Paths.get("sq/products/product3/index.html"),
      Paths.get("sq/company/index.html"),
      Paths.get("sq/company/jobs/index.html"),
      Paths.get("sq/company/news/index.html"),
      Paths.get("sq/company/contact/index.html"));
    FileMatcher.Result match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local/sub"));
    // sq/news is preferred to sq/products because of lexicographic order
    assertThat(match.sqPrefix()).isEqualTo(Paths.get("sq/news"));
  }

  // @Disabled("Only used to investigate performance issues like SLE-344")
  @Test
  public void performance_test() throws Exception {
    int depthFactor = 10;
    int sqNbPerFolder = 10;
    int sqDepth = 5;
    int ideNbPerFolder = 10;
    int ideDepth = 3;
    List<Path> idePaths = generateChildren(Paths.get("local/sub/src/main/java/com/mycompany/myapp/foo/bar"), ideNbPerFolder, depthFactor, ideDepth * depthFactor);
    System.out.println("IDE file count: " + idePaths.size());
    assertThat(idePaths).hasSize((int) Math.pow(ideNbPerFolder, ideDepth + 1));
    List<Path> sqPaths = generateChildren(Paths.get("sq/src/main/java/com/mycompany/myapp/foo/bar"), sqNbPerFolder, depthFactor, sqDepth * depthFactor);
    System.out.println("SQ file count: " + sqPaths.size());
    assertThat(sqPaths).hasSize((int) Math.pow(sqNbPerFolder, sqDepth + 1));
    Instant start = Instant.now();
    FileMatcher.Result match = fileMatcher.match(sqPaths, idePaths);
    System.out.println(Duration.between(start, Instant.now()).toMillis() + "ms ellapsed");
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local/sub/src/main/java/com/mycompany/myapp/foo/bar"));
    // sq/folder0/[...]/folder0 is preferred to other sq/folderx because of lexicographic order
    assertThat(match.sqPrefix()).isEqualTo(Paths.get(
      "sq/src/main/java/com/mycompany/myapp/foo/bar/folder0/extra49/extra48/extra47/extra46/extra45/extra44/extra43/extra42/extra41/folder0/extra39/extra38/extra37/extra36/extra35/extra34/extra33/extra32/extra31"));
  }

  private List<Path> generateChildren(Path parent, int count, int everyDepth, int depth) {
    List<Path> result = new ArrayList<>();
    if (depth == 0) {
      for (int i = 0; i < count; i++) {
        result.add(parent.resolve("file" + i + ".txt"));
      }
    } else if (depth % everyDepth == 0) {
      for (int i = 0; i < count; i++) {
        Path current = parent.resolve("folder" + i);
        result.addAll(generateChildren(current, count, everyDepth, depth - 1));
      }
    } else {
      Path current = parent.resolve("extra" + depth);
      result.addAll(generateChildren(current, count, everyDepth, depth - 1));
    }
    return result;
  }

  @Test
  public void should_return_most_common_prefixes() {
    List<Path> idePaths = Arrays.asList(
      Paths.get("local1/src/main/java/A.java"),
      Paths.get("local1/src/main/java/B.java"),
      Paths.get("local2/src/main/java/B.java"));

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
  public void should_favor_deepest_common_path() {
    List<Path> idePaths = Arrays.asList(
      Paths.get("local1/pom.xml"),
      Paths.get("local1/build.properties"),
      Paths.get("local1/src/main/java/com/foo/A.java"));

    List<Path> sqPaths = Arrays.asList(
      Paths.get("sq1/pom.xml"),
      Paths.get("sq1/build.properties"),
      Paths.get("sq2/src/main/java/com/foo/A.java")

    );
    FileMatcher.Result match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local1"));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get("sq2"));
  }

  @Test
  public void should_disfavor_path_having_multiple_matches() {
    List<Path> idePaths = Arrays.asList(
      Paths.get("local1/pom.xml"),
      Paths.get("local1/build.properties"),
      Paths.get("local1/src/A.java"));

    List<Path> sqPaths = Arrays.asList(
      Paths.get("sq1/pom.xml"),
      Paths.get("sq1/build.properties"),
      Paths.get("sq2/pom.xml"),
      Paths.get("sq2/build.properties"),
      Paths.get("sq3/pom.xml"),
      Paths.get("sq3/build.properties"),
      Paths.get("sq4/src/A.java")

    );
    FileMatcher.Result match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local1"));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get("sq4"));
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
