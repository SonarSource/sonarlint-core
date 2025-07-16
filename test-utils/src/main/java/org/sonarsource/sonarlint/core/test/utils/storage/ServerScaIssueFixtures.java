/*
 * SonarLint Core - Test Utils
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
package org.sonarsource.sonarlint.core.test.utils.storage;

import java.util.List;
import java.util.UUID;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerScaIssue;

public class ServerScaIssueFixtures {

  private ServerScaIssueFixtures() {
    // utility class
  }

  public static ServerScaIssueBuilder aServerScaIssue() {
    return new ServerScaIssueBuilder();
  }

  public static class ServerScaIssueBuilder {
    private UUID key = UUID.randomUUID();
    private ServerScaIssue.Type type = ServerScaIssue.Type.VULNERABILITY;
    private ServerScaIssue.Severity severity = ServerScaIssue.Severity.HIGH;
    private ServerScaIssue.Status status = ServerScaIssue.Status.OPEN;
    private String packageName = "com.example.vulnerable";
    private String packageVersion = "1.0.0";
    private List<ServerScaIssue.Transition> transitions = List.of(ServerScaIssue.Transition.CONFIRM, ServerScaIssue.Transition.ACCEPT);

    public ServerScaIssueBuilder withKey(UUID key) {
      this.key = key;
      return this;
    }

    public ServerScaIssueBuilder withType(ServerScaIssue.Type type) {
      this.type = type;
      return this;
    }

    public ServerScaIssueBuilder withSeverity(ServerScaIssue.Severity severity) {
      this.severity = severity;
      return this;
    }

    public ServerScaIssueBuilder withStatus(ServerScaIssue.Status status) {
      this.status = status;
      return this;
    }

    public ServerScaIssueBuilder withPackageName(String packageName) {
      this.packageName = packageName;
      return this;
    }

    public ServerScaIssueBuilder withPackageVersion(String packageVersion) {
      this.packageVersion = packageVersion;
      return this;
    }

    public ServerScaIssueBuilder withTransitions(List<ServerScaIssue.Transition> transitions) {
      this.transitions = transitions;
      return this;
    }

    public ServerScaIssue build() {
      return new ServerScaIssue(key, type, severity, status, packageName, packageVersion, transitions);
    }
  }
}
