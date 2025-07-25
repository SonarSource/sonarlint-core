/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking;

import java.util.List;
import java.util.UUID;

public class DependencyRiskDto {
  private final UUID id;
  private final Type type;
  private final Severity severity;
  private final Status status;
  private final String packageName;
  private final String packageVersion;
  private final List<Transition> transitions;

  public DependencyRiskDto(UUID id, Type type, Severity severity, Status status, String packageName, String packageVersion, List<Transition> transitions) {
    this.id = id;
    this.type = type;
    this.severity = severity;
    this.status = status;
    this.packageName = packageName;
    this.packageVersion = packageVersion;
    this.transitions = transitions;
  }

  public UUID getId() {
    return id;
  }

  public Type getType() {
    return type;
  }

  public Severity getSeverity() {
    return severity;
  }

  public Status getStatus() {
    return status;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getPackageVersion() {
    return packageVersion;
  }

  public List<Transition> getTransitions() {
    return transitions;
  }

  public enum Severity {
    INFO, LOW, MEDIUM, HIGH, BLOCKER
  }

  public enum Type {
    VULNERABILITY, PROHIBITED_LICENSE
  }

  public enum Status {
    OPEN, CONFIRM, ACCEPT, SAFE
  }

  public enum Transition {
    CONFIRM, REOPEN, SAFE, FIXED, ACCEPT
  }
}
