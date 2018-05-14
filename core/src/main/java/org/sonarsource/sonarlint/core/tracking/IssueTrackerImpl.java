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
package org.sonarsource.sonarlint.core.tracking;

import java.util.ArrayList;
import java.util.Collection;

/**
 * {@inheritDoc}
 */
public class IssueTrackerImpl implements IssueTracker {

  /**
   * Local issue tracking: baseIssues are existing issue, nextIssues are raw issues coming from the analysis.
   * Server issue tracking: baseIssues are server issues, nextIssues are the existing issue, coming from local issue tracking.
   */
  @Override
  public Collection<Trackable> apply(Collection<Trackable> baseIssues, Collection<Trackable> nextIssues, boolean inheritSeverity) {
    Collection<Trackable> trackedIssues = new ArrayList<>();
    Tracking<Trackable, Trackable> tracking = new Tracker<>().track(() -> nextIssues, () -> baseIssues);

    tracking.getMatchedRaws().entrySet().stream()
      .map(e -> new CombinedTrackable(e.getValue(), e.getKey(), inheritSeverity))
      .forEach(trackedIssues::add);

    for (Trackable next : tracking.getUnmatchedRaws()) {
      if (next.getServerIssueKey() != null) {
        // not matched with server anymore
        next = new DisconnectedTrackable(next);
      } else if (next.getCreationDate() == null) {
        // first time we see this issue locally
        next = new LeakedTrackable(next);
      }
      trackedIssues.add(next);
    }

    return trackedIssues;
  }
}
