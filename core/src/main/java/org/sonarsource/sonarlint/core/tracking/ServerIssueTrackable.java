/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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

import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;

public class ServerIssueTrackable implements Trackable {

  private final ServerIssue serverIssue;

  public ServerIssueTrackable(ServerIssue serverIssue) {
    this.serverIssue = serverIssue;
  }

  @Override
  public Issue getIssue() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getRuleKey() {
    return serverIssue.ruleKey();
  }

  @Override
  public String getRuleName() {
    throw new UnsupportedOperationException();
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
    return serverIssue.message();
  }

  @Override
  public Integer getLine() {
    return serverIssue.line();
  }

  @Override
  public Integer getLineHash() {
    return serverIssue.checksum().hashCode();
  }

  @Override
  public TextRange getTextRange() {
    return new TextRange(serverIssue.line());
  }

  @Override
  public Integer getTextRangeHash() {
    // note: not available from server API
    return null;
  }

  @Override
  public Long getCreationDate() {
    return serverIssue.creationDate().toEpochMilli();
  }

  @Override
  public String getServerIssueKey() {
    return serverIssue.key();
  }

  @Override
  public boolean isResolved() {
    return !serverIssue.resolution().isEmpty();
  }

  @Override
  public String getAssignee() {
    return serverIssue.assigneeLogin();
  }
}
