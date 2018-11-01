/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import org.sonarsource.sonarlint.core.util.ReversePathTree;

import static java.util.Collections.reverseOrder;

public class FileMatcher {
  private final ReversePathTree reversePathTree;

  public FileMatcher(ReversePathTree reversePathTree) {
    this.reversePathTree = reversePathTree;
  }

  public Result match(List<Path> sqRelativePaths, List<Path> ideRelativePaths) {
    Map<Result, Integer> results = new LinkedHashMap<>();
    BiFunction<Result, Integer, Integer> incrementer = (p, i) -> i != null ? (i + 1) : 1;

    sqRelativePaths.forEach(reversePathTree::index);

    for (Path ide : ideRelativePaths) {
      ReversePathTree.Match match = reversePathTree.findLongestSuffixMatches(ide);
      if (match.matchLen() > 0) {
        Path idePrefix = getIdePrefix(ide, match);

        for (Path sqPrefix : match.matchPrefixes()) {
          Result r = new Result(idePrefix, sqPrefix);
          results.compute(r, incrementer);
        }
      }
    }

    return mostCommonResult(results);
  }

  private static Path getIdePrefix(Path idePath, ReversePathTree.Match match) {
    int prefixLen = idePath.getNameCount() - match.matchLen();
    if (prefixLen > 0) {
      return idePath.subpath(0, idePath.getNameCount() - match.matchLen());
    }
    return Paths.get("");
  }

  private static Result mostCommonResult(Map<Result, Integer> prefixes) {
    Comparator<Map.Entry<Result, Integer>> c = Comparator.comparing(Map.Entry::getValue);
    c = c.thenComparing(x -> x.getKey().idePrefix.getNameCount(), reverseOrder());

    return prefixes.entrySet().stream()
      .max(c)
      .map(Map.Entry::getKey)
      .orElse(new Result(Paths.get(""), Paths.get("")));
  }

  public static class Result {
    private Path idePrefix;
    private Path sqPrefix;

    Result(Path idePrefix, Path sqPrefix) {
      this.idePrefix = idePrefix;
      this.sqPrefix = sqPrefix;
    }

    public Path idePrefix() {
      return idePrefix;
    }

    public Path sqPrefix() {
      return sqPrefix;
    }

    @Override public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Result result = (Result) o;
      return Objects.equals(idePrefix, result.idePrefix) && Objects.equals(sqPrefix, result.sqPrefix);
    }

    @Override public int hashCode() {
      return Objects.hash(idePrefix, sqPrefix);
    }
  }
}
