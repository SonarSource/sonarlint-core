/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.rules;

import org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality;

public class ImpactDto {
  private final SoftwareQuality softwareQuality;
  private final String softwareQualityLabel;
  private final ImpactSeverity impactSeverity;
  private final String impactSeverityLabel;

  public ImpactDto(SoftwareQuality softwareQuality, String softwareQualityLabel, ImpactSeverity impactSeverity, String impactSeverityLabel) {
    this.softwareQuality = softwareQuality;
    this.softwareQualityLabel = softwareQualityLabel;
    this.impactSeverity = impactSeverity;
    this.impactSeverityLabel = impactSeverityLabel;
  }

  public SoftwareQuality getSoftwareQuality() {
    return softwareQuality;
  }

  public String getSoftwareQualityLabel() {
    return softwareQualityLabel;
  }

  public ImpactSeverity getImpactSeverity() {
    return impactSeverity;
  }

  public String getImpactSeverityLabel() {
    return impactSeverityLabel;
  }
}
