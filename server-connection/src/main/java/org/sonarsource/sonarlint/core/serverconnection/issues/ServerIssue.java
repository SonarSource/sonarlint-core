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
package org.sonarsource.sonarlint.core.serverconnection.issues;

import java.time.Instant;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public abstract class ServerIssue<G extends ServerIssue<G>> {
  private String key;
  private boolean resolved;
  private String ruleKey;
  private String message;
  private String filePath;
  private Instant creationDate;
  private String userSeverity;
  private String type;

  protected ServerIssue(String key, boolean resolved, String ruleKey, String message, String filePath, Instant creationDate, @Nullable String userSeverity, String type) {
    this.key = key;
    this.resolved = resolved;
    this.ruleKey = ruleKey;
    this.message = message;
    this.filePath = filePath;
    this.creationDate = creationDate;
    this.userSeverity = userSeverity;
    this.type = type;
  }

  public String getKey() {
    return key;
  }

  public boolean isResolved() {
    return resolved;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getMessage() {
    return message;
  }

  public String getFilePath() {
    return filePath;
  }

  public Instant getCreationDate() {
    return creationDate;
  }

  @CheckForNull
  public String getUserSeverity() {
    return userSeverity;
  }

  public String getType() {
    return type;
  }

  public G setKey(String key) {
    this.key = key;
    return (G) this;
  }

  public G setRuleKey(String ruleKey) {
    this.ruleKey = ruleKey;
    return (G) this;
  }

  public G setMessage(String message) {
    this.message = message;
    return (G) this;
  }

  public G setFilePath(String filePath) {
    this.filePath = filePath;
    return (G) this;
  }

  public G setCreationDate(Instant creationDate) {
    this.creationDate = creationDate;
    return (G) this;
  }

  public G setUserSeverity(@Nullable String userSeverity) {
    this.userSeverity = userSeverity;
    return (G) this;
  }

  public G setType(String type) {
    this.type = type;
    return (G) this;
  }

  public G setResolved(boolean resolved) {
    this.resolved = resolved;
    return (G) this;
  }

}
