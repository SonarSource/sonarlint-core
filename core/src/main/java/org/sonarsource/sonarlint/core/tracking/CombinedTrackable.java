/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
public class CombinedTrackable extends WrappedTrackable {

  private final String serverIssueKey;
  private final Long creationDate;
  private final boolean resolved;
  private final String assignee;

  public CombinedTrackable(Trackable base, Trackable next) {
    super(next);

    // Warning: do not store a reference to base, as it might never get garbage collected
    this.creationDate = base.getCreationDate();
    this.serverIssueKey = base.getServerIssueKey();
    this.resolved = base.isResolved();
    this.assignee = base.getAssignee();
  }

  @Override
  public Long getCreationDate() {
    return creationDate;
  }

  @Override
  public String getServerIssueKey() {
    return serverIssueKey;
  }

  @Override
  public boolean isResolved() {
    return resolved;
  }

  @Override
  public String getAssignee() {
    return assignee;
  }
}
