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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.sonarsource.sonarlint.core.util.ReversePathTree;

public class FileMatcher {
  private ReversePathTree reversePathTree;

  public FileMatcher(ReversePathTree reversePathTree) {
    this.reversePathTree = reversePathTree;
  }

  public Result match(List<Path> sqRelativePaths, List<Path> localRelativePaths) {
    Map<Path, Integer> localPrefixes = new HashMap<>();
    Map<Path, Integer> sqPrefixes = new HashMap<>();
    BiFunction<Path, Integer, Integer> incrementer = (p, i) -> i != null ? i + 1 : 1;

    sqRelativePaths.forEach(reversePathTree::index);

    for (Path local : localRelativePaths) {
      ReversePathTree.Match match = reversePathTree.findLongestSuffixMatches(local);
      for (Path sqPrefix : match.matchPrefixes()) {
        sqPrefixes.compute(sqPrefix, incrementer);
      }

      if (match.matchLen() > 0) {
        Path localPrefix = getLocalPrefix(local, match);
        localPrefixes.compute(localPrefix, incrementer);
      }
    }

    return new Result(mostCommonPrefix(localPrefixes), mostCommonPrefix(sqPrefixes));
  }

  private Path getLocalPrefix(Path localPath, ReversePathTree.Match match) {
    int prefixLen = localPath.getNameCount() - match.matchLen();
    if (prefixLen > 0) {
      return localPath.subpath(0, localPath.getNameCount() - match.matchLen());
    }
    return Paths.get("");
  }

  public static class Result {
    private Path localPrefix;
    private Path sqPrefix;

    private Result(Path localPrefix, Path sqPrefix) {
      this.localPrefix = localPrefix;
      this.sqPrefix = sqPrefix;
    }

    public Path mostCommonLocalPrefix() {
      return localPrefix;
    }

    public Path mostCommonSqPrefix() {
      return sqPrefix;
    }
  }

  private Path mostCommonPrefix(Map<Path, Integer> prefixes) {
    return prefixes.entrySet().stream()
      .max(Comparator.comparing(Map.Entry::getValue))
      .map(Map.Entry::getKey)
      .orElse(Paths.get(""));
  }
}
