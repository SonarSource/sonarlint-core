/*
 * SonarLint Core - Test Utils
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;

public class ServerDependencyRiskFixtures {

  private ServerDependencyRiskFixtures() {
    // utility class
  }

  public static ServerDependencyRiskBuilder aServerDependencyRisk() {
    return new ServerDependencyRiskBuilder();
  }

  public static class ServerDependencyRiskBuilder {
    private UUID key = UUID.randomUUID();
    private ServerDependencyRisk.Type type = ServerDependencyRisk.Type.VULNERABILITY;
    private ServerDependencyRisk.Severity severity = ServerDependencyRisk.Severity.HIGH;
    private ServerDependencyRisk.SoftwareQuality quality = ServerDependencyRisk.SoftwareQuality.SECURITY;
    private ServerDependencyRisk.Status status = ServerDependencyRisk.Status.OPEN;
    private String packageName = "com.example.vulnerable";
    private String packageVersion = "1.0.0";
    @Nullable
    private String vulnerabilityId = null;
    @Nullable
    private String cvssScore = null;
    private List<ServerDependencyRisk.Transition> transitions = List.of(ServerDependencyRisk.Transition.CONFIRM, ServerDependencyRisk.Transition.ACCEPT);

    public ServerDependencyRiskBuilder withKey(UUID key) {
      this.key = key;
      return this;
    }

    public ServerDependencyRiskBuilder withType(ServerDependencyRisk.Type type) {
      this.type = type;
      return this;
    }

    public ServerDependencyRiskBuilder withSeverity(ServerDependencyRisk.Severity severity) {
      this.severity = severity;
      return this;
    }

    public ServerDependencyRiskBuilder withQuality(ServerDependencyRisk.SoftwareQuality quality) {
      this.quality = quality;
      return this;
    }

    public ServerDependencyRiskBuilder withStatus(ServerDependencyRisk.Status status) {
      this.status = status;
      return this;
    }

    public ServerDependencyRiskBuilder withPackageName(String packageName) {
      this.packageName = packageName;
      return this;
    }

    public ServerDependencyRiskBuilder withPackageVersion(String packageVersion) {
      this.packageVersion = packageVersion;
      return this;
    }

    public ServerDependencyRiskBuilder withVulnerabilityId(String vulnerabilityId) {
      this.vulnerabilityId = vulnerabilityId;
      return this;
    }

    public ServerDependencyRiskBuilder withCvssScore(String cvssScore) {
      this.cvssScore = cvssScore;
      return this;
    }

    public ServerDependencyRiskBuilder withTransitions(List<ServerDependencyRisk.Transition> transitions) {
      this.transitions = transitions;
      return this;
    }

    public ServerDependencyRisk build() {
      return new ServerDependencyRisk(key, type, severity, quality, status, packageName, packageVersion,
        vulnerabilityId, cvssScore, transitions);
    }
  }
}
