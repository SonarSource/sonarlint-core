/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.issues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record ServerDependencyRisk(UUID key, Type type, Severity severity, SoftwareQuality quality,
                                   Status status, String packageName, String packageVersion, List<Transition> transitions) {

  public ServerDependencyRisk withStatus(Status newStatus) {
    var newTransitions = new ArrayList<>(Arrays.asList(Transition.values()));
    newTransitions.remove(Transition.FIXED);
    newTransitions.remove(newStatus.equals(Status.OPEN) ? Transition.REOPEN : Transition.valueOf(newStatus.name()));
    return new ServerDependencyRisk(key, type, severity, quality, newStatus, packageName, packageVersion, newTransitions);
  }

  public enum Severity {
    INFO, LOW, MEDIUM, HIGH, BLOCKER
  }

  public enum SoftwareQuality {
    MAINTAINABILITY,
    RELIABILITY,
    SECURITY
  }

  public enum Type {
    VULNERABILITY, PROHIBITED_LICENSE
  }

  public enum Status {
    OPEN, CONFIRM, ACCEPT, SAFE, FIXED
  }

  public enum Transition {
    CONFIRM, REOPEN, SAFE, FIXED, ACCEPT
  }
}
