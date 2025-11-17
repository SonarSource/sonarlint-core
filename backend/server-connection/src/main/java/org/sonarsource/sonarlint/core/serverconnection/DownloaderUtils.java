/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;

public class DownloaderUtils {

  private DownloaderUtils() {
    // Utility class
  }

  public static SoftwareQuality parseProtoSoftwareQuality(Common.Impact protoImpact) {
    if (!protoImpact.hasSoftwareQuality() || protoImpact.getSoftwareQuality() == Common.SoftwareQuality.UNKNOWN_IMPACT_QUALITY) {
      throw new IllegalArgumentException("Unknown or missing software quality");
    }
    return SoftwareQuality.valueOf(protoImpact.getSoftwareQuality().name());
  }

  public static ImpactSeverity parseProtoImpactSeverity(Common.Impact protoImpact) {
    if (!protoImpact.hasSeverity() || protoImpact.getSeverity() == Common.ImpactSeverity.UNKNOWN_IMPACT_SEVERITY) {
      throw new IllegalArgumentException("Unknown or missing impact severity");
    }
    return ImpactSeverity.mapSeverity(protoImpact.getSeverity().name());
  }

}
