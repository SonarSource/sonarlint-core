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
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;

public class DefaultServerIssue implements ServerIssue {
  private String key;
  private String resolution;
  private String ruleKey;
  private int line;
  private String message;
  private String lineHash;
  private String assigneeLogin;
  private String filePath;
  private Instant creationDate;
  private String severity;
  private String type;

  @Override
  public String key() {
    return key;
  }

  @Override
  public String resolution() {
    return resolution;
  }

  @Override
  public String ruleKey() {
    return ruleKey;
  }

  @Override
  public int line() {
    return line;
  }

  @Override
  public String message() {
    return message;
  }

  @Override
  public String lineHash() {
    return lineHash;
  }

  @Override
  public String assigneeLogin() {
    return assigneeLogin;
  }

  @Override
  public String filePath() {
    return filePath;
  }

  @Override
  public Instant creationDate() {
    return creationDate;
  }

  @Override
  public String severity() {
    return severity;
  }

  @Override
  public String type() {
    return type;
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

  public DefaultServerIssue setLine(int line) {
    this.line = line;
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
}
