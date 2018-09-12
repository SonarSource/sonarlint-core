/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;

public class DefaultServerIssue implements ServerIssue {
  private String key;
  private String resolution;
  private String ruleKey;
  private int line;
  private String message;
  private String checksum;
  private String assigneeLogin;
  private String moduleKey;
  private String filePath;
  private boolean manualSeverity;
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
  public String checksum() {
    return checksum;
  }

  @Override
  public String assigneeLogin() {
    return assigneeLogin;
  }

  @Override
  public String moduleKey() {
    return moduleKey;
  }

  @Override
  public String filePath() {
    return filePath;
  }

  @Override
  public boolean manualSeverity() {
    return manualSeverity;
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

  public DefaultServerIssue setChecksum(String checksum) {
    this.checksum = checksum;
    return this;
  }

  public DefaultServerIssue setAssigneeLogin(String assigneeLogin) {
    this.assigneeLogin = assigneeLogin;
    return this;
  }

  public DefaultServerIssue setModuleKey(String moduleKey) {
    this.moduleKey = moduleKey;
    return this;
  }

  public DefaultServerIssue setFilePath(String filePath) {
    this.filePath = filePath;
    return this;
  }

  public DefaultServerIssue setManualSeverity(boolean manualSeverity) {
    this.manualSeverity = manualSeverity;
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
  
  public DefaultServerIssue setType(@Nullable String type) {
    this.type = type;
    return this;
  }
}
