/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.telemetry.gessie;

import org.apache.commons.lang3.SystemUtils;
import org.sonarsource.sonarlint.core.telemetry.InternalDebug;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServerAttributesProvider;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.monitoring.MonitoringUserIdStore;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.payload.IDESupportedLanguageViewedPayload;

public class GessieEventPublisher {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final GessieService gessieService;
  private final MonitoringUserIdStore monitoringUserIdStore;
  private final TelemetryServerAttributesProvider telemetryServerAttributesProvider;
  private final TelemetryClientConstantAttributesDto telemetryConstantAttributes;

  public GessieEventPublisher(GessieService gessieService, MonitoringUserIdStore monitoringUserIdStore,
    TelemetryServerAttributesProvider telemetryServerAttributesProvider, InitializeParams initializeParams) {
    this.gessieService = gessieService;
    this.monitoringUserIdStore = monitoringUserIdStore;
    this.telemetryServerAttributesProvider = telemetryServerAttributesProvider;
    this.telemetryConstantAttributes = initializeParams.getTelemetryConstantAttributes();
  }

  public void supportedLanguageViewed(String configScopeId) {
    if (!gessieService.isEnabled()) {
      return;
    }
    
    monitoringUserIdStore.getOrCreate().map(Object::toString).ifPresentOrElse(localUserId -> {
      GessieConnectionInfo connectionInfo = telemetryServerAttributesProvider.getGessieConnectionInfo(configScopeId).orElse(null);
      gessieService.supportedLanguageViewed(new IDESupportedLanguageViewedPayload(
        localUserId,
        telemetryConstantAttributes.getProductVersion(),
        SystemUtils.OS_NAME,
        connectionInfo != null ? connectionInfo.connectionType() : null,
        connectionInfo != null ? connectionInfo.userUuid() : null,
        connectionInfo != null ? connectionInfo.organizationUuidV4() : null,
        connectionInfo != null ? connectionInfo.sqsInstallationId() : null
      ));
    }, () -> {
      if (InternalDebug.isEnabled()) {
        LOG.warn("Could not retrieve local user ID — skipping Gessie event");
      }
    });
  }

}
