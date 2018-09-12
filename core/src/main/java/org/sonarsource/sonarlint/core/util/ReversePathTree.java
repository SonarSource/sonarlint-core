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
package org.sonarsource.sonarlint.core.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReversePathTree {
  private Node root = new Node();

  public void index(Path path) {
    Node currentNode = root;

    for (int i = path.getNameCount() - 1; i >= 0; i--) {
      currentNode = currentNode.children.computeIfAbsent(path.getName(i).toString(), e -> new Node());
    }
  }

  public Match findLongestSuffixMatches(Path path) {
    Node currentNode = root;
    int matchLen = 0;

    while (matchLen < path.getNameCount()) {
      String nextEl = path.getName(path.getNameCount() - matchLen - 1).toString();
      Node nextNode = currentNode.children.get(nextEl);
      if (nextNode == null) {
        break;
      }
      matchLen++;
      currentNode = nextNode;
    }

    return collectAllPrefixes(currentNode, matchLen);
  }

  private Match collectAllPrefixes(Node node, int matchLen) {
    List<Path> paths = new ArrayList<>();
    if (matchLen > 0) {
      collectPrefixes(node, Paths.get(""), paths);
    }
    return new Match(paths, matchLen);
  }

  private void collectPrefixes(Node node, Path currentPath, List<Path> paths) {
    if (node.children.isEmpty()) {
      paths.add(currentPath);
    }

    for (Map.Entry<String, Node> child : node.children.entrySet()) {
      Path childPath = Paths.get(child.getKey()).resolve(currentPath);
      collectPrefixes(child.getValue(), childPath, paths);
    }
  }

  private static class Node {
    Map<String, Node> children = new LinkedHashMap<>();
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
