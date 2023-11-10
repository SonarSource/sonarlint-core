/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.prefix;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileTreeMatcherTests {
  private final FileTreeMatcher fileMatcher = new FileTreeMatcher();

  @Test
  void simple_case_without_prefixes() {
    List<Path> paths = Collections.singletonList(Paths.get("project1/src/main/java/File.java"));
    var match = fileMatcher.match(paths, paths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get(""));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get(""));
  }

  @Test
  void simple_case_with_prefixes() {
    List<Path> idePaths = Collections.singletonList(Paths.get("local/src/main/java/File.java"));
    List<Path> sqPaths = Collections.singletonList(Paths.get("sq/src/main/java/File.java"));
    var match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local"));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get("sq"));
  }

  @Test
  void no_match() {
    List<Path> idePaths = Collections.singletonList(Paths.get("local/src/main/java/File1.java"));
    List<Path> sqPaths = Collections.singletonList(Paths.get("sq/src/main/java/File2.java"));
    var match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get(""));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get(""));
  }

  @Test
  void empty_project_in_ide() {
    List<Path> idePaths = Collections.emptyList();
    List<Path> sqPaths = Collections.singletonList(Paths.get("sq/src/main/java/File2.java"));
    var match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get(""));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get(""));
  }

  @Test
  void should_return_shortest_sq_prefix_if_there_are_ties() {
    List<Path> idePaths = Arrays.asList(
      Paths.get("pom.xml"));

    List<Path> sqPaths = Arrays.asList(
      Paths.get("aq1/module2/pom.xml"),
      Paths.get("aq2/pom.xml"),
      Paths.get("pom.xml"),
      Paths.get("aq1/module1/pom.xml"));
    var match = fileMatcher.match(sqPaths, idePaths);
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
  void more_complex_test_with_multiple_files() throws Exception {
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
    var match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local/sub"));
    // sq/news is preferred to sq/products because of lexicographic order
    assertThat(match.sqPrefix()).isEqualTo(Paths.get("sq/news"));
  }

  @Disabled("Only used to investigate performance issues like SLCORE-266")
  @Test
  void performance_test_worst_case() throws Exception {
    var depthFactor = 10;
    var sqNbPerFolder = 10;
    var sqDepth = 5;
    var ideNbPerFolder = 10;
    var ideDepth = 3;
    var idePaths = generateChildren(Paths.get("local/sub/src/main/java/com/mycompany/myapp/foo/bar"), ideNbPerFolder, depthFactor, ideDepth * depthFactor);
    System.out.println("IDE file count: " + idePaths.size());
    assertThat(idePaths).hasSize((int) Math.pow(ideNbPerFolder, ideDepth + 1));
    var sqPaths = generateChildren(Paths.get("sq/src/main/java/com/mycompany/myapp/foo/bar"), sqNbPerFolder, depthFactor, sqDepth * depthFactor);
    System.out.println("SQ file count: " + sqPaths.size());
    assertThat(sqPaths).hasSize((int) Math.pow(sqNbPerFolder, sqDepth + 1));
    var start = Instant.now();
    var match = fileMatcher.match(sqPaths, idePaths);
    System.out.println(Duration.between(start, Instant.now()).toMillis() + "ms ellapsed");
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local/sub/src/main/java/com/mycompany/myapp/foo/bar"));
    // sq/folder0/[...]/folder0 is preferred to other sq/folderx because of lexicographic order
    assertThat(match.sqPrefix()).isEqualTo(Paths.get(
      "sq/src/main/java/com/mycompany/myapp/foo/bar/folder0/extra49/extra48/extra47/extra46/extra45/extra44/extra43/extra42/extra41/folder0/extra39/extra38/extra37/extra36/extra35/extra34/extra33/extra32/extra31"));
  }

  @Disabled("Only used to investigate performance issues like SLCORE-266")
  @Test
  void performance_test_only_index_files_with_same_filename() throws Exception {
    var depthFactor = 10;
    var sqNbPerFolder = 10;
    var sqDepth = 5;
    // IDE contains only paths with filename 'file1.txt'
    var ideNbPerFolder = 1;
    var ideDepth = 3;
    performance_test(depthFactor, sqNbPerFolder, sqDepth, ideNbPerFolder, ideDepth);
  }

  private void performance_test(int depthFactor, int sqNbPerFolder, int sqDepth, int ideNbPerFolder, int ideDepth) {
    var idePaths = generateChildren(Paths.get("local/sub/src/main/java/com/mycompany/myapp/foo/bar"), ideNbPerFolder, depthFactor, ideDepth * depthFactor);
    System.out.println("IDE file count: " + idePaths.size());
    assertThat(idePaths).hasSize((int) Math.pow(ideNbPerFolder, ideDepth + 1));
    var sqPaths = generateChildren(Paths.get("sq/src/main/java/com/mycompany/myapp/foo/bar"), sqNbPerFolder, depthFactor, sqDepth * depthFactor);
    System.out.println("SQ file count: " + sqPaths.size());
    assertThat(sqPaths).hasSize((int) Math.pow(sqNbPerFolder, sqDepth + 1));
    var start = Instant.now();
    var match = fileMatcher.match(sqPaths, idePaths);
    System.out.println(Duration.between(start, Instant.now()).toMillis() + "ms ellapsed");
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local/sub/src/main/java/com/mycompany/myapp/foo/bar"));
    // sq/folder0/[...]/folder0 is preferred to other sq/folderx because of lexicographic order
    assertThat(match.sqPrefix()).isEqualTo(Paths.get(
      "sq/src/main/java/com/mycompany/myapp/foo/bar/folder0/extra49/extra48/extra47/extra46/extra45/extra44/extra43/extra42/extra41/folder0/extra39/extra38/extra37/extra36/extra35/extra34/extra33/extra32/extra31"));
  }

  private List<Path> generateChildren(Path parent, int count, int everyDepth, int depth) {
    List<Path> result = new ArrayList<>();
    if (depth == 0) {
      for (var i = 0; i < count; i++) {
        result.add(parent.resolve("file" + i + ".txt"));
      }
    } else if (depth % everyDepth == 0) {
      for (var i = 0; i < count; i++) {
        var current = parent.resolve("folder" + i);
        result.addAll(generateChildren(current, count, everyDepth, depth - 1));
      }
    } else {
      var current = parent.resolve("extra" + depth);
      result.addAll(generateChildren(current, count, everyDepth, depth - 1));
    }
    return result;
  }

  @Test
  void should_return_most_common_prefixes() {
    List<Path> idePaths = Arrays.asList(
      Paths.get("local1/src/main/java/A.java"),
      Paths.get("local1/src/main/java/B.java"),
      Paths.get("local2/src/main/java/B.java"));

    List<Path> sqPaths = Arrays.asList(
      Paths.get("sq1/src/main/java/A.java"),
      Paths.get("sq2/src/main/java/A.java"),
      Paths.get("sq1/src/main/java/B.java")

    );
    var match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local1"));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get("sq1"));
  }

  @Test
  void should_favor_deepest_common_path() {
    List<Path> idePaths = Arrays.asList(
      Paths.get("local1/pom.xml"),
      Paths.get("local1/build.properties"),
      Paths.get("local1/src/main/java/com/foo/A.java"));

    List<Path> sqPaths = Arrays.asList(
      Paths.get("sq1/pom.xml"),
      Paths.get("sq1/build.properties"),
      Paths.get("sq2/src/main/java/com/foo/A.java")

    );
    var match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local1"));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get("sq2"));
  }

  @Test
  void should_disfavor_path_having_multiple_matches() {
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
    var match = fileMatcher.match(sqPaths, idePaths);
    assertThat(match.idePrefix()).isEqualTo(Paths.get("local1"));
    assertThat(match.sqPrefix()).isEqualTo(Paths.get("sq4"));
  }

  @Test
  void verify_equals_and_hashcode_of_result() {
    var r1 = new FileTreeMatcher.Result(Paths.get("ide1"), Paths.get("sq1"));
    var r2 = new FileTreeMatcher.Result(Paths.get("ide2"), Paths.get("sq1"));
    var r3 = new FileTreeMatcher.Result(Paths.get("ide1"), Paths.get("sq2"));
    var r4 = new FileTreeMatcher.Result(Paths.get("ide1"), Paths.get("sq1"));

    assertThat(r1.equals(r1)).isTrue();
    assertThat(r1.equals(r4)).isTrue();
    assertThat(r1.hashCode()).isEqualTo(r4.hashCode());

    assertThat(r1.equals(r3)).isFalse();
    assertThat(r3.equals(r2)).isFalse();
    assertThat(r1.equals(new Object())).isFalse();
    assertThat(r1.equals(null)).isFalse();
  }
}
