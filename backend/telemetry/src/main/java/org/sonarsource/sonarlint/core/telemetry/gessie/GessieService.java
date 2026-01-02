/*
 * SonarLint Core - Telemetry
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

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.UUID;
import org.sonarsource.sonarlint.core.commons.dogfood.DogfoodEnvironmentDetectionService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.telemetry.common.TelemetryUserSetting;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieEvent;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieMetadata;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.payload.MessagePayload;

import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.GESSIE_TELEMETRY;
import static org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieMetadata.SonarLintDomain;

public class GessieService {

  private final boolean isGessieFeatureEnabled;
  private final TelemetryClientConstantAttributesDto telemetryConstantAttributes;
  private final GessieHttpClient client;
  private final TelemetryUserSetting userSetting;

  public GessieService(InitializeParams initializeParams, DogfoodEnvironmentDetectionService dogfoodEnvironmentDetectionService, GessieHttpClient client,
    TelemetryUserSetting userSetting) {
    this.isGessieFeatureEnabled = initializeParams.getBackendCapabilities().contains(GESSIE_TELEMETRY) && dogfoodEnvironmentDetectionService.isDogfoodEnvironment();
    this.telemetryConstantAttributes = initializeParams.getTelemetryConstantAttributes();
    this.client = client;
    this.userSetting = userSetting;
  }

  @PostConstruct
  public void onStartup() {
    if (isGessieFeatureEnabled && userSetting.isTelemetryEnabledByUser()) {
      client.postEvent(new GessieEvent(
        new GessieMetadata(UUID.randomUUID(),
          new GessieMetadata.GessieSource(SonarLintDomain.fromProductKey(telemetryConstantAttributes.getProductKey())),
          "Analytics.Editor.PluginActivated",
          Long.toString(Instant.now().toEpochMilli()),
          "0"),
        new MessagePayload("Gessie integration test event", "slcore_start")));
    }
  }
}
