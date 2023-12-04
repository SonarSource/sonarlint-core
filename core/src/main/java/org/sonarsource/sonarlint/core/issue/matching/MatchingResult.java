/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.issue.matching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;

/**
 * Store the result of matching of issues.
 *
 * @param <LEFT>  type of the issues that are in the first collection
 * @param <RIGHT> type of the issues that are in the second collection
 */
public class MatchingResult<LEFT, RIGHT> {

  /**
   * Matched issues -> a left issue is associated to a right issue
   */
  private final IdentityHashMap<LEFT, RIGHT> leftToRight = new IdentityHashMap<>();
  private final IdentityHashMap<RIGHT, LEFT> rightToLeft = new IdentityHashMap<>();

  private final Collection<LEFT> lefts;
  private final Collection<RIGHT> rights;

  public MatchingResult(Collection<LEFT> leftIssues, Collection<RIGHT> rightIssues) {
    this.lefts = leftIssues;
    this.rights = rightIssues;
  }

  /**
   * Returns an Iterable to be traversed when matching issues. That means
   * that the traversal does not fail if method {@link #match(LEFT, RIGHT)}
   * is called.
   */
  public Iterable<LEFT> getUnmatchedLefts() {
    List<LEFT> result = new ArrayList<>();
    for (LEFT left : lefts) {
      if (!leftToRight.containsKey(left)) {
        result.add(left);
      }
    }
    return result;
  }

  public Map<LEFT, RIGHT> getMatchedLefts() {
    return leftToRight;
  }

  /**
   * The right issues that are not matched by a left issue.
   */
  public Iterable<RIGHT> getUnmatchedRights() {
    List<RIGHT> result = new ArrayList<>();
    for (RIGHT right : rights) {
      if (!rightToLeft.containsKey(right)) {
        result.add(right);
      }
    }
    return result;
  }

  void match(LEFT left, RIGHT right) {
    leftToRight.put(left, right);
    rightToLeft.put(right, left);
  }

  boolean isComplete() {
    return leftToRight.size() == lefts.size();
  }

  @CheckForNull
  public RIGHT getMatch(LEFT left) {
    return leftToRight.get(left);
  }

}
