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

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CachingIssueTrackerTests {

  private final IssueTrackerCache cache = mock(IssueTrackerCache.class);
  private final CachingIssueTracker tracker = new CachingIssueTracker(cache);

  @Test
  void clear_should_clear_the_embedded_issue_cache_too() {
    tracker.clear();
    verify(cache).clear();
  }

  @Test
  void shutdown_should_shutdown_the_embedded_issue_cache_too() {
    tracker.shutdown();
    verify(cache).shutdown();
  }
}
