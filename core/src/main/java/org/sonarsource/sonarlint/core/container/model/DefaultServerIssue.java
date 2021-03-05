/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssueLocation;

public class DefaultServerIssue implements ServerIssue {
  private String key;
  private String resolution;
  private String ruleKey;
  private String message;
  private String lineHash;
  private String assigneeLogin;
  private String filePath;
  private Instant creationDate;
  private String severity;
  private String type;
  private List<Flow<ServerIssueLocation>> flows = new ArrayList<>();
  private TextRange textRange;
  private String codeSnippet;
  private DefaultServerLocation location;

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public String getResolution() {
    return resolution;
  }

  @Override
  public String getRuleKey() {
    return ruleKey;
  }

  @Override
  public ServerIssueLocation getLocation() {
    if (location == null) {
      location = new DefaultServerLocation(filePath, getTextRange(), message, codeSnippet);
    }
    return location;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public String getLineHash() {
    return lineHash;
  }

  @Override
  public String getAssigneeLogin() {
    return assigneeLogin;
  }

  @Override
  public String getFilePath() {
    return filePath;
  }

  @Override
  public Instant getCreationDate() {
    return creationDate;
  }

  @Override
  public String getSeverity() {
    return severity;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public TextRange getTextRange() {
    return textRange;
  }

  @Override
  public List<Flow<ServerIssueLocation>> getFlows() {
    return flows;
  }

  @Override
  public String getCodeSnippet() {
    return codeSnippet;
  }

  public DefaultServerIssue setKey(String key) {
    this.key = key;
    return this;
  }

  public DefaultServerIssue setResolution(String resolution) {
    this.resolution = resolution;
    return this;
  }

  public DefaultServerIssue setRuleKey(String ruleKey) {
    this.ruleKey = ruleKey;
    return this;
  }

  public DefaultServerIssue setMessage(String message) {
    this.message = message;
    return this;
  }

  public DefaultServerIssue setLineHash(String lineHash) {
    this.lineHash = lineHash;
    return this;
  }

  public DefaultServerIssue setAssigneeLogin(String assigneeLogin) {
    this.assigneeLogin = assigneeLogin;
    return this;
  }

  public DefaultServerIssue setFilePath(String filePath) {
    this.filePath = filePath;
    return this;
  }

  public DefaultServerIssue setCreationDate(Instant creationDate) {
    this.creationDate = creationDate;
    return this;
  }

  public DefaultServerIssue setSeverity(String severity) {
    this.severity = severity;
    return this;
  }

  public DefaultServerIssue setType(String type) {
    this.type = type;
    return this;
  }

  public DefaultServerIssue setTextRange(@Nullable TextRange textRange) {
    this.textRange = textRange;
    return this;
  }

  public DefaultServerIssue setFlows(List<Flow<ServerIssueLocation>> flows) {
    this.flows = flows;
    return this;
  }

  public DefaultServerIssue setCodeSnippet(String codeSnippet) {
    this.codeSnippet = codeSnippet;
    return this;
  }
}
