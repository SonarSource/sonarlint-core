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
package org.sonarsource.sonarlint.core.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;

public class ReversePathTree {
  private final OptimizedNode root = new OptimizedNode();

  public void index(Path path) {
    OptimizedNode currentNode = root;

    for (int i = path.getNameCount() - 1; i >= 0; i--) {
      currentNode = currentNode.computeChildrenIfAbsent(path.getName(i));
    }
    currentNode.terminal = true;
  }

  public Match findLongestSuffixMatches(Path path) {
    OptimizedNode currentNode = root;
    int matchLen = 0;

    while (matchLen < path.getNameCount()) {
      Path nextEl = path.getName(path.getNameCount() - matchLen - 1);
      OptimizedNode nextNode = currentNode.getChild(nextEl);
      if (nextNode == null) {
        break;
      }
      matchLen++;
      currentNode = nextNode;
    }

    return collectAllPrefixes(currentNode, matchLen);
  }

  private static Match collectAllPrefixes(OptimizedNode node, int matchLen) {
    List<Path> paths = new ArrayList<>();
    if (matchLen > 0) {
      collectPrefixes(node, Paths.get(""), paths);
    }
    return new Match(paths, matchLen);
  }

  private static void collectPrefixes(OptimizedNode node, Path currentPath, List<Path> paths) {
    if (node.terminal) {
      paths.add(currentPath);
    }

    for (Map.Entry<Path, OptimizedNode> child : node.childrenEntrySet()) {
      Path childPath = child.getKey().resolve(currentPath);
      collectPrefixes(child.getValue(), childPath, paths);
    }
  }

  /**
   * Since it is very common that a node will have only one child, we save memory by lazily creating a children LinkedHashMap only when a second item is added.
   */
  private static class OptimizedNode {
    boolean terminal = false;
    Path singleChildKey = null;
    OptimizedNode singleChildValue;
    Map<Path, OptimizedNode> children = null;

    public OptimizedNode computeChildrenIfAbsent(Path name) {
      if (name.equals(singleChildKey)) {
        return singleChildValue;
      }
      if (singleChildKey == null && children == null) {
        singleChildKey = name;
        singleChildValue = new OptimizedNode();
        return singleChildValue;
      } else if (children == null) {
        children = new HashMap<>();
        children.put(singleChildKey, singleChildValue);
        OptimizedNode value = new OptimizedNode();
        children.put(name, value);
        singleChildKey = null;
        singleChildValue = null;
        return value;
      } else {
        return children.computeIfAbsent(name, e -> new OptimizedNode());
      }
    }

    public Set<Map.Entry<Path, OptimizedNode>> childrenEntrySet() {
      if (singleChildKey == null && children == null) {
        return Collections.emptySet();
      } else if (children == null) {
        return Collections.singleton(new AbstractMap.SimpleEntry<Path, OptimizedNode>(singleChildKey, singleChildValue));
      } else {
        return children.entrySet();
      }
    }

    @CheckForNull
    public OptimizedNode getChild(Path name) {
      if (singleChildKey == null && children == null) {
        return null;
      } else if (children == null) {
        return singleChildKey.equals(name) ? singleChildValue : null;
      } else {
        return children.get(name);
      }
    }
  }

  public static class Match {
    private List<Path> paths;
    private int matchLen;

    private Match(List<Path> paths, int matchLen) {
      this.paths = paths;
      this.matchLen = matchLen;
    }

    public List<Path> matchPrefixes() {
      return paths;
    }

    public int matchLen() {
      return matchLen;
    }

  }
}
