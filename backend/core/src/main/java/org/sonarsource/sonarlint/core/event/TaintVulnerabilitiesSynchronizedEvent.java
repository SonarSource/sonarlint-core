/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.event;

import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.storage.UpdateSummary;

public class TaintVulnerabilitiesSynchronizedEvent {
  private final String connectionId;
  private final String sonarProjectKey;
  private final String sonarBranch;
  private final UpdateSummary<ServerTaintIssue> summary;

  public TaintVulnerabilitiesSynchronizedEvent(String connectionId, String sonarProjectKey, String sonarBranch, UpdateSummary<ServerTaintIssue> summary) {
    this.connectionId = connectionId;
    this.sonarProjectKey = sonarProjectKey;
    this.sonarBranch = sonarBranch;
    this.summary = summary;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public String getSonarProjectKey() {
    return sonarProjectKey;
  }

  public String getSonarBranch() {
    return sonarBranch;
  }

  public UpdateSummary<ServerTaintIssue> getSummary() {
    return summary;
  }
}
