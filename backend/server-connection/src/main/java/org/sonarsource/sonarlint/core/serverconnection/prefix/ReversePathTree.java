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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

class ReversePathTree {
  private final Node root = new MultipleChildrenNode();

  public void index(Path path) {
    Node parent = null;
    var currentNode = root;
    Path currentNodePath = null;

    for (var i = path.getNameCount() - 1; i >= 0; i--) {
      var childNodePath = path.getName(i);
      var result = currentNode.computeChildrenIfAbsent(parent, currentNodePath, childNodePath);
      parent = result[0];
      currentNode = result[1];
      currentNodePath = childNodePath;
    }

    currentNode.setTerminal(true);
  }

  public Match findLongestSuffixMatches(Path path) {
    var currentNode = root;
    var matchLen = 0;

    while (matchLen < path.getNameCount()) {
      var nextEl = path.getName(path.getNameCount() - matchLen - 1);
      var nextNode = currentNode.getChild(nextEl);
      if (nextNode == null) {
        break;
      }
      matchLen++;
      currentNode = nextNode;
    }

    return collectAllPrefixes(currentNode, matchLen);
  }

  private static Match collectAllPrefixes(Node node, int matchLen) {
    List<Path> paths = new ArrayList<>();
    if (matchLen > 0) {
      collectPrefixes(node, Paths.get(""), paths);
    }
    return new Match(paths, matchLen);
  }

  private static void collectPrefixes(Node node, Path currentPath, List<Path> paths) {
    if (node.isTerminal()) {
      paths.add(currentPath);
    }

    for (Map.Entry<Path, Node> child : node.childrenEntrySet()) {
      var childPath = child.getKey().resolve(currentPath);
      collectPrefixes(child.getValue(), childPath, paths);
    }
  }

  /**
   * Since it is very common that a node will have only one child, we save memory by lazily creating a children HashMap only when a second item is added.
   */
  private interface Node {
    Node[] computeChildrenIfAbsent(Node parent, Path currentNodePath, Path childNodePath);

    Set<Map.Entry<Path, Node>> childrenEntrySet();

    Node getChild(Path name);

    void setTerminal(boolean b);

    boolean isTerminal();

    void put(Path path, Node node);
  }

  private abstract static class AbstractNode implements Node {
    private boolean terminal;

    @Override
    public final boolean isTerminal() {
      return terminal;
    }

    @Override
    public final void setTerminal(boolean b) {
      this.terminal = b;
    }
  }

  private static class SingleChildNode extends AbstractNode {
    @Nullable
    private Path singleChildKey;
    @Nullable
    private Node singleChildValue;

    @Override
    public Node[] computeChildrenIfAbsent(Node parent, Path currentNodePath, Path childNodePath) {
      if (singleChildKey == null) {
        put(childNodePath, new SingleChildNode());
        return new Node[] {this, singleChildValue};
      }
      if (childNodePath.equals(singleChildKey)) {
        return new Node[] {this, singleChildValue};
      }
      var child = new SingleChildNode();
      var replacement = new MultipleChildrenNode();
      replacement.put(singleChildKey, singleChildValue);
      replacement.put(childNodePath, child);
      parent.put(currentNodePath, replacement);
      return new Node[] {replacement, child};
    }

    @Override
    public Set<Map.Entry<Path, Node>> childrenEntrySet() {
      if (singleChildKey == null) {
        return Collections.emptySet();
      } else {
        return Collections.singleton(new AbstractMap.SimpleEntry<>(singleChildKey, singleChildValue));
      }
    }

    @Override
    public void put(Path path, Node node) {
      this.singleChildKey = path;
      this.singleChildValue = node;
    }

    @Override
    @CheckForNull
    public Node getChild(Path name) {
      return name.equals(singleChildKey) ? singleChildValue : null;
    }
  }

  private static class MultipleChildrenNode extends AbstractNode {

    private final Map<Path, Node> children = new HashMap<>();

    @Override
    public Node[] computeChildrenIfAbsent(Node parent, Path currentNodePath, Path childNodePath) {
      return new Node[] {this, children.computeIfAbsent(childNodePath, e -> new SingleChildNode())};
    }

    @Override
    public Set<Map.Entry<Path, Node>> childrenEntrySet() {
      return children.entrySet();
    }

    @CheckForNull
    @Override
    public Node getChild(Path name) {
      return children.get(name);
    }

    @Override
    public void put(Path path, Node node) {
      children.put(path, node);
    }

  }

  public static class Match {
    private final List<Path> paths;
    private final int matchLen;

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
