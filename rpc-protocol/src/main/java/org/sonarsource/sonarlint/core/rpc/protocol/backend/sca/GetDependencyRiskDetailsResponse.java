/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.sca;

import java.util.List;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.AffectedPackageDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;

public class GetDependencyRiskDetailsResponse {
  private final UUID key;
  private final DependencyRiskDto.Severity severity;
  private final DependencyRiskDto.SoftwareQuality quality;
  private final String packageName;
  private final String version;
  private final DependencyRiskDto.Type type;
  @Nullable
  private final String vulnerabilityId;
  @Nullable
  private final String description;
  private final List<AffectedPackageDto> affectedPackages;

  public GetDependencyRiskDetailsResponse(UUID key, DependencyRiskDto.Severity severity, DependencyRiskDto.SoftwareQuality quality, String packageName,
    String version, DependencyRiskDto.Type type, @Nullable String vulnerabilityId, @Nullable String description, List<AffectedPackageDto> affectedPackages) {
    this.key = key;
    this.severity = severity;
    this.quality = quality;
    this.packageName = packageName;
    this.version = version;
    this.type = type;
    this.vulnerabilityId = vulnerabilityId;
    this.description = description;
    this.affectedPackages = affectedPackages;
  }

  public UUID getKey() {
    return key;
  }

  public DependencyRiskDto.Severity getSeverity() {
    return severity;
  }

  public DependencyRiskDto.SoftwareQuality getQuality() {
    return quality;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getVersion() {
    return version;
  }

  public DependencyRiskDto.Type getType() {
    return type;
  }

  @CheckForNull
  public String getVulnerabilityId() {
    return vulnerabilityId;
  }

  @CheckForNull
  public String getDescription() {
    return description;
  }

  public List<AffectedPackageDto> getAffectedPackages() {
    return affectedPackages;
  }
}
