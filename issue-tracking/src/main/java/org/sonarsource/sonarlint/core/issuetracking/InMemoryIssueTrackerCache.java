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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIssueTrackerCache<T> implements IssueTrackerCache<T> {

  private final Map<String, Collection<Trackable<T>>> cache = new ConcurrentHashMap<>();

  @Override
  public boolean isFirstAnalysis(String file) {
    return !cache.containsKey(file);
  }

  @Override
  public Collection<Trackable<T>> getCurrentTrackables(String file) {
    return cache.getOrDefault(file, Collections.emptyList());
  }

  @Override
  public Collection<Trackable<T>> getLiveOrFail(String file) {
    var trackables = cache.get(file);
    if (trackables == null) {
      throw new IllegalStateException("file should have been already analyzed: " + file);
    }
    return trackables;
  }

  @Override
  public void put(String file, Collection<Trackable<T>> trackables) {
    cache.put(file, trackables);
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @Override
  public void shutdown() {
    // nothing to do
  }
}
