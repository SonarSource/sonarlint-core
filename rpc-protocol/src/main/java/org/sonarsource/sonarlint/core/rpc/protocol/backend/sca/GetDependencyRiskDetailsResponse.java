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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.sca;

import java.util.List;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.AffectedPackageDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ScaIssueDto;

public class GetDependencyRiskDetailsResponse {
  private final String key;
  private final ScaIssueDto.Severity severity;
  private final String packageName;
  private final String version;
  private final ScaIssueDto.Type type;
  private final String vulnerabilityId;
  private final String description;
  private final List<AffectedPackageDto> affectedPackages;

  public GetDependencyRiskDetailsResponse(String key, ScaIssueDto.Severity severity, String packageName, String version, ScaIssueDto.Type type, String vulnerabilityId,
    String description, List<AffectedPackageDto> affectedPackages) {
    this.key = key;
    this.severity = severity;
    this.packageName = packageName;
    this.version = version;
    this.type = type;
    this.vulnerabilityId = vulnerabilityId;
    this.description = description;
    this.affectedPackages = affectedPackages;
  }

  public String getKey() {
    return key;
  }

  public ScaIssueDto.Severity getSeverity() {
    return severity;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getVersion() {
    return version;
  }

  public ScaIssueDto.Type getType() {
    return type;
  }

  public String getVulnerabilityId() {
    return vulnerabilityId;
  }

  public String getDescription() {
    return description;
  }

  public List<AffectedPackageDto> getAffectedPackages() {
    return affectedPackages;
  }
}
