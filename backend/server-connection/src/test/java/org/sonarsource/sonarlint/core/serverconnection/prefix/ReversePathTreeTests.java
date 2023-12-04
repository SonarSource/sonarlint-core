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

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReversePathTreeTests {
  private final ReversePathTree tree = new ReversePathTree();

  @Test
  void should_return_matching_prefix() {
    tree.index(Paths.get("A/src/main/java/File.java"));

    var match = tree.findLongestSuffixMatches(Paths.get("B/src/main/java/File.java"));

    assertThat(match.matchLen()).isEqualTo(4);
    assertThat(match.matchPrefixes()).containsExactly(Paths.get("A"));
  }

  @Test
  void should_return_matching_prefixes() {
    tree.index(Paths.get("project1/src/main/java/File.java"));
    tree.index(Paths.get("project2/src/main/java/File.java"));
    tree.index(Paths.get("project2/src/test/java/File.java"));

    var match = tree.findLongestSuffixMatches(Paths.get("src/main/java/File.java"));

    assertThat(match.matchLen()).isEqualTo(4);
    assertThat(match.matchPrefixes()).containsExactlyInAnyOrder(Paths.get("project1"), Paths.get("project2"));
  }

  @Test
  void should_return_empty_prefix_if_full_match() {
    tree.index(Paths.get("project1/src/main/java/File.java"));
    tree.index(Paths.get("project2/src/main/java/File.java"));
    tree.index(Paths.get("project2/src/test/java/File.java"));

    var match = tree.findLongestSuffixMatches(Paths.get("project2/src/main/java/File.java"));

    assertThat(match.matchLen()).isEqualTo(5);
    assertThat(match.matchPrefixes()).containsExactly(Paths.get(""));
  }

  @Test
  void should_return_empty_if_no_match() {
    tree.index(Paths.get("project1/src/main/java/File.java"));
    tree.index(Paths.get("project2/src/main/java/File.java"));
    tree.index(Paths.get("project2/src/test/java/File.java"));

    var match = tree.findLongestSuffixMatches(Paths.get("File2.java"));

    assertThat(match.matchLen()).isEqualTo(0);
    assertThat(match.matchPrefixes()).isEmpty();
  }

  @Test
  void should_return_matches_that_are_part_of_other_matches() {
    tree.index(Paths.get("project1/A/pom.xml"));
    tree.index(Paths.get("project1/pom.xml"));
    tree.index(Paths.get("pom.xml"));
    var match = tree.findLongestSuffixMatches(Paths.get("pom.xml"));
    assertThat(match.matchLen()).isEqualTo(1);
    assertThat(match.matchPrefixes()).containsOnly(Paths.get(""), Paths.get("project1"), Paths.get("project1/A"));
  }

}
