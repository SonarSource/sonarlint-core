/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.tracking;

import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.serverconnection.ServerIssue;

public class ServerIssueTrackable implements Trackable {

  private final ServerIssue serverIssue;

  public ServerIssueTrackable(ServerIssue serverIssue) {
    this.serverIssue = serverIssue;
  }

  @Override
  public Object getClientObject() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getRuleKey() {
    return serverIssue.ruleKey();
  }

  @Override
  public String getSeverity() {
    return serverIssue.severity();
  }

  @Override
  public String getType() {
    return serverIssue.type();
  }

  @Override
  public String getMessage() {
    return serverIssue.getMessage();
  }

  @Override
  public Integer getLine() {
    return serverIssue.getLine();
  }

  @Override
  public Integer getLineHash() {
    return serverIssue.getLineHash().hashCode();
  }

  @Override
  public org.sonarsource.sonarlint.core.issuetracking.TextRange getTextRange() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Integer getTextRangeHash() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Long getCreationDate() {
    return serverIssue.creationDate().toEpochMilli();
  }

  @Override
  public String getServerIssueKey() {
    return serverIssue.getKey();
  }

  @Override
  public boolean isResolved() {
    return serverIssue.resolved();
  }
}
