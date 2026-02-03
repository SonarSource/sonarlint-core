/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.tracking.matching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  private final Collection<LEFT> lefts;

  public MatchingResult(Collection<LEFT> leftIssues) {
    this.lefts = leftIssues;
  }

  /**
   * Returns an Iterable to be traversed when matching issues. That means
   * that the traversal does not fail if method {@link #recordMatch(LEFT, RIGHT)}
   * is called.
   */
  public Iterable<L> getUnmatchedLefts() {
    List<L> result = new ArrayList<>();
    for (L left : lefts) {
      if (!leftToRight.containsKey(left)) {
        result.add(left);
      }
    }
    return result;
  }

  public Map<LEFT, RIGHT> getMatchedLefts() {
    return leftToRight;
  }

  void recordMatch(LEFT left, RIGHT right) {
    leftToRight.put(left, right);
  }

  boolean isComplete() {
    return leftToRight.size() == lefts.size();
  }

  @CheckForNull
  public RIGHT getMatch(LEFT left) {
    return leftToRight.get(left);
  }

  public Optional<RIGHT> getMatchOpt(LEFT left) {
    return Optional.ofNullable(getMatch(left));
  }

}
