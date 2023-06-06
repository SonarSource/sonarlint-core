/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.backend.issue;

public class ChangeIssueStatusParams {

  private final String configurationScopeId;
  private final String issueKey;
  private final IssueStatus newStatus;
  private final boolean isTaintIssue;

  public ChangeIssueStatusParams(String configurationScopeId, String issueKey, IssueStatus newStatus,
    boolean isTaintIssue) {
    this.configurationScopeId = configurationScopeId;
    this.issueKey = issueKey;
    this.newStatus = newStatus;
    this.isTaintIssue = isTaintIssue;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public String getIssueKey() {
    return issueKey;
  }

  public IssueStatus getNewStatus() {
    return newStatus;
  }

  public boolean isTaintIssue() {
    return isTaintIssue;
  }
}
