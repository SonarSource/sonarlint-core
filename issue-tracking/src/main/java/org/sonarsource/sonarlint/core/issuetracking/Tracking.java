/*
 * SonarLint Issue Tracking
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
package org.sonarsource.sonarlint.core.issuetracking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Store the state of tracking of issues.
 *
 * @param <R> type of the "raw" trackables that are in the incoming collection
 * @param <B> type of the base trackables that are in the current collection
 */
public class Tracking<R extends Trackable, B extends Trackable> {

  /**
   * Matched issues -> a raw issue is associated to a base issue
   */
  private final IdentityHashMap<R, B> rawToBase = new IdentityHashMap<>();
  private final IdentityHashMap<B, R> baseToRaw = new IdentityHashMap<>();

  private final Collection<R> raws;
  private final Collection<B> bases;

  public Tracking(Supplier<Collection<R>> rawTrackableSupplier, Supplier<Collection<B>> baseTrackableSupplier) {
    this.raws = rawTrackableSupplier.get();
    this.bases = baseTrackableSupplier.get();
  }

  /**
   * Returns an Iterable to be traversed when matching issues. That means
   * that the traversal does not fail if method {@link #match(Trackable, Trackable)}
   * is called.
   */
  public Iterable<R> getUnmatchedRaws() {
    List<R> result = new ArrayList<>();
    for (R r : raws) {
      if (!rawToBase.containsKey(r)) {
        result.add(r);
      }
    }
    return result;
  }

  public Map<R, B> getMatchedRaws() {
    return rawToBase;
  }

  /**
   * The base issues that are not matched by a raw issue and that need to be closed.
   */
  public Iterable<B> getUnmatchedBases() {
    List<B> result = new ArrayList<>();
    for (B b : bases) {
      if (!baseToRaw.containsKey(b)) {
        result.add(b);
      }
    }
    return result;
  }

  void match(R raw, B base) {
    rawToBase.put(raw, base);
    baseToRaw.put(base, raw);
  }

  boolean isComplete() {
    return rawToBase.size() == raws.size();
  }

}
