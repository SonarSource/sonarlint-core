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

public interface IssueTrackerCache<T> {

  boolean isFirstAnalysis(String file);

  Collection<Trackable<T>> getCurrentTrackables(String file);

  Collection<Trackable<T>> getLiveOrFail(String file);

  void put(String file, Collection<Trackable<T>> trackables);

  /**
   * Empty the cache, delete everything.
   */
  void clear();

  /**
   * Shutdown the cache. This is the time for persistent implementations to flush everything to storage.
   */
  void shutdown();

}
