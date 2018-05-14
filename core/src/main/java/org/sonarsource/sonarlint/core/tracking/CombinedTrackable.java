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

/**
 * Combine a new Trackable ("next") with a previous state ("base")
 */
class CombinedTrackable extends AbstractTrackable {
  /**
   * Local issue tracking: base are existing issues, next are raw issues coming from the analysis. We don't want to inherit severity and type
   * so that latest analysis always overrides them.
   * Server issue tracking: base are server issues, next are the existing issue, coming from local issue tracking. We want to inherit severity and type
   * so that the server issues override analyzers.
   */
  CombinedTrackable(Trackable base, Trackable next, boolean inheritSeverity) {
    super(next);

    // Warning: do not store a reference to base, as it might never get garbage collected
    this.creationDate = base.getCreationDate();
    this.serverIssueKey = base.getServerIssueKey();
    this.resolved = base.isResolved();
    this.assignee = base.getAssignee();
    if (inheritSeverity) {
      this.severity = base.getSeverity();
      if (base.getType() != null) {
        // this can be null for old SQ servers that didn't have issue types yet
        this.type = base.getType();
      }
    }
  }
}
