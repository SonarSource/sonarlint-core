/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.tracking;

import java.util.Collection;
import java.util.Collections;

public class CachingIssueTrackerImpl extends IssueTrackerImpl implements CachingIssueTracker {

  private final IssueTrackerCache cache;

  public CachingIssueTrackerImpl(IssueTrackerCache cache) {
    this.cache = cache;
  }

  /**
   * {@inheritDoc}
   *
   * If this is the first analysis, leave creation date as null.
   */
  @Override
  public synchronized Collection<Trackable> matchAndTrackAsNew(String file, Collection<Trackable> trackables) {
    Collection<Trackable> tracked;
    if (cache.isFirstAnalysis(file)) {
      tracked = trackables;
    } else {
      tracked = apply(cache.getCurrentTrackables(file), trackables, false);
    }
    cache.put(file, tracked);
    return tracked;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized Collection<Trackable> matchAndTrackAsBase(String file, Collection<Trackable> trackables) {
    // store issues (ProtobufIssueTrackable) are of no use since they can't be used in markers. There should have been
    // an analysis before that set the live issues for the file (even if it is empty)
    Collection<Trackable> current = cache.getLiveOrFail(file);
    if (current.isEmpty()) {
      // whatever is the base, if current is empty, then nothing to do
      return Collections.emptyList();
    }
    Collection<Trackable> tracked = apply(trackables, current, true);
    cache.put(file, tracked);
    return tracked;
  }

  public void clear() {
    cache.clear();
  }

  public void shutdown() {
    cache.shutdown();
  }
}
