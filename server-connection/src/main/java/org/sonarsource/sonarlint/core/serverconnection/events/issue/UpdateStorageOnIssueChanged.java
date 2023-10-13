/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection.events.issue;

import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.events.ServerEventHandler;

public class UpdateStorageOnIssueChanged implements ServerEventHandler<IssueChangedEvent> {
  private final ConnectionStorage storage;

  public UpdateStorageOnIssueChanged(ConnectionStorage storage) {
    this.storage = storage;
  }

  @Override
  public void handle(IssueChangedEvent event) {
    var userSeverity = event.getUserSeverity();
    var userType = event.getUserType();
    var resolved = event.getResolved();
    var projectKey = event.getProjectKey();
    event.getImpactedIssueKeys().forEach(issueKey -> update(event, projectKey, userSeverity, userType, resolved, issueKey));
  }

  private void update(IssueChangedEvent event, String projectKey, IssueSeverity userSeverity, RuleType userType, Boolean resolved, String issueKey) {
    var updated = updateNormalIssue(projectKey, userSeverity, userType, resolved, issueKey);
    if (!updated) {
      var isTaintUpdate = updateTaintIssue(projectKey, userSeverity, userType, resolved, issueKey);
      if (isTaintUpdate) {
        event.addImpactedTaintIssueKey(issueKey);
      }
    }
  }

  private boolean updateNormalIssue(String projectKey, IssueSeverity userSeverity, RuleType userType, Boolean resolved, String issueKey) {
    return storage.project(projectKey).findings().updateIssue(issueKey, issue -> {
      if (userSeverity != null) {
        issue.setUserSeverity(userSeverity);
      }
      if (userType != null) {
        issue.setType(userType);
      }
      if (resolved != null) {
        issue.setResolved(resolved);
      }
    });
  }

  private boolean updateTaintIssue(String projectKey, IssueSeverity userSeverity, RuleType userType, Boolean resolved, String issueKey) {
    return storage.project(projectKey).findings().updateTaintIssue(issueKey, issue -> {
      if (userSeverity != null) {
        issue.setSeverity(userSeverity);
      }
      if (userType != null) {
        issue.setType(userType);
      }
      if (resolved != null) {
        issue.setResolved(resolved);
      }
    });
  }
}
