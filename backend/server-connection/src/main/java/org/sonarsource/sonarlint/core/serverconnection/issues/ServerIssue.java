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
package org.sonarsource.sonarlint.core.serverconnection.issues;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;

public abstract class ServerIssue<G extends ServerIssue<G>> implements ServerFinding {
  private String key;
  private boolean resolved;
  private String ruleKey;
  private String message;
  private Path filePath;
  private Instant creationDate;
  private IssueSeverity userSeverity;
  private RuleType type;
  private Map<SoftwareQuality, ImpactSeverity> impacts;

  protected ServerIssue(String key, boolean resolved, String ruleKey, String message, Path filePath, Instant creationDate, @Nullable IssueSeverity userSeverity, RuleType type,
    Map<SoftwareQuality, ImpactSeverity> impacts) {
    this.key = key;
    this.resolved = resolved;
    this.ruleKey = ruleKey;
    this.message = message;
    this.filePath = filePath;
    this.creationDate = creationDate;
    this.userSeverity = userSeverity;
    this.type = type;
    this.impacts = impacts;
  }

  public String getKey() {
    return key;
  }

  public boolean isResolved() {
    return resolved;
  }

  @Override
  public String getRuleKey() {
    return ruleKey;
  }

  public String getMessage() {
    return message;
  }

  public Path getFilePath() {
    return filePath;
  }

  public Instant getCreationDate() {
    return creationDate;
  }

  @CheckForNull
  public IssueSeverity getUserSeverity() {
    return userSeverity;
  }

  public RuleType getType() {
    return type;
  }

  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return impacts;
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

  public G setFilePath(Path filePath) {
    this.filePath = filePath;
    return (G) this;
  }

  public G setCreationDate(Instant creationDate) {
    this.creationDate = creationDate;
    return (G) this;
  }

  public G setUserSeverity(@Nullable IssueSeverity userSeverity) {
    this.userSeverity = userSeverity;
    return (G) this;
  }

  public G setType(RuleType type) {
    this.type = type;
    return (G) this;
  }

  public G setResolved(boolean resolved) {
    this.resolved = resolved;
    return (G) this;
  }

  public G setImpacts(Map<SoftwareQuality, ImpactSeverity> impacts) {
    this.impacts = impacts;
    return (G) this;
  }

}
