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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.util.ReversePathTree;

import static java.util.Collections.reverseOrder;

public class FileMatcher {

  public Result match(List<Path> sqRelativePaths, List<Path> ideRelativePaths) {
    ReversePathTree reversePathTree = new ReversePathTree();

    Map<Result, Double> resultScores = new LinkedHashMap<>();

    sqRelativePaths.forEach(reversePathTree::index);

    for (Path ide : ideRelativePaths) {
      ReversePathTree.Match match = reversePathTree.findLongestSuffixMatches(ide);
      if (match.matchLen() > 0) {
        Path idePrefix = getIdePrefix(ide, match);

        for (Path sqPrefix : match.matchPrefixes()) {
          Result r = new Result(idePrefix, sqPrefix);
          resultScores.compute(r, (p, i) -> computeScore(i, match));
        }
      }
    }

    return higherScoreResult(resultScores);
  }

  private static double computeScore(@Nullable Double currentScore, ReversePathTree.Match match) {
    double matchScore = (double) match.matchLen() / match.matchPrefixes().size();
    return currentScore != null ? (currentScore.doubleValue() + matchScore) : matchScore;
  }

  private static Path getIdePrefix(Path idePath, ReversePathTree.Match match) {
    int prefixLen = depth(idePath) - match.matchLen();
    if (prefixLen > 0) {
      return idePath.subpath(0, depth(idePath) - match.matchLen());
    }
    return Paths.get("");
  }

  private static Result higherScoreResult(Map<Result, Double> prefixes) {
    // Prefere higher score
    Comparator<Map.Entry<Result, Double>> c = Comparator.comparing(Map.Entry::getValue);
    c = c
      // fallback on prefix depth
      .thenComparing(x -> depth(x.getKey().sqPrefix), reverseOrder())
      // fallback on prefix lexicographic order
      .thenComparing(x -> x.getKey().sqPrefix.toString(), reverseOrder());

    return prefixes.entrySet().stream()
      .max(c)
      .map(Map.Entry::getKey)
      .orElse(new Result(Paths.get(""), Paths.get("")));
  }

  private static int depth(Path path) {
    return path.toString().length() == 0 ? 0 : path.getNameCount();
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

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Result result = (Result) o;
      return Objects.equals(idePrefix, result.idePrefix) && Objects.equals(sqPrefix, result.sqPrefix);
    }

    @Override
    public int hashCode() {
      return Objects.hash(idePrefix, sqPrefix);
    }
  }
}
