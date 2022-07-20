/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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
import org.sonarsource.sonarlint.core.serverconnection.events.ServerEventHandler;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerIssueStore;

public class UpdateStorageOnIssueChanged implements ServerEventHandler<IssueChangedEvent> {
  private final ServerIssueStore serverIssueStore;

  public UpdateStorageOnIssueChanged(ServerIssueStore serverIssueStore) {
    this.serverIssueStore = serverIssueStore;
  }

  @Override
  public void handle(IssueChangedEvent event) {
    var userSeverity = event.getUserSeverity();
    var userType = event.getUserType();
    var resolved = event.getResolved();
    event.getImpactedIssueKeys().forEach(issueKey -> update(userSeverity, userType, resolved, issueKey));
  }

  private void update(IssueSeverity userSeverity, RuleType userType, Boolean resolved, String issueKey) {
    var updated = updateNormalIssue(userSeverity, userType, resolved, issueKey);
    if (!updated) {
      updateTaintIssue(userSeverity, userType, resolved, issueKey);
    }
  }

  private boolean updateNormalIssue(IssueSeverity userSeverity, RuleType userType, Boolean resolved, String issueKey) {
    return serverIssueStore.updateIssue(issueKey, issue -> {
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

  private void updateTaintIssue(IssueSeverity userSeverity, RuleType userType, Boolean resolved, String issueKey) {
    serverIssueStore.updateTaintIssue(issueKey, issue -> {
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
