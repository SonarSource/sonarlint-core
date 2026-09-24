/*
 * SonarLint Core - Telemetry
 * Copyright (C) SonarSource Sàrl
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
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiAgentIntegrationStateObservedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationCliStateObservedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationFailureCategory;
import org.sonarsource.sonarlint.core.telemetry.common.TelemetryUserSetting;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieEvent;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieMetadata;
import org.sonarsource.sonarlint.core.telemetry.gessie.event.payload.MessagePayload;

import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.GESSIE_TELEMETRY;
import static org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus.FAILED;
import static org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieMetadata.SonarLintDomain;

public class GessieService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final boolean isGessieFeatureEnabled;
  private final TelemetryClientConstantAttributesDto telemetryConstantAttributes;
  private final GessieHttpClient client;
  private final TelemetryUserSetting userSetting;

  public GessieService(InitializeParams initializeParams, GessieHttpClient client, TelemetryUserSetting userSetting) {
    this.isGessieFeatureEnabled = initializeParams.getBackendCapabilities().contains(GESSIE_TELEMETRY);
    this.telemetryConstantAttributes = initializeParams.getTelemetryConstantAttributes();
    this.client = client;
    this.userSetting = userSetting;
  }

  @PostConstruct
  public void onStartup() {
    emit("Analytics.Editor.PluginActivated", new MessagePayload("Gessie integration test event", "slcore_start"));
  }

  public void aiIntegrationAction(@Nullable AiIntegrationActionParams params) {
    if (params == null || params.getAction() == null || params.getStatus() == null || params.getHost() == null) {
      return;
    }
    var failureCategory = params.getFailureCategory();
    if (params.getStatus() != FAILED) {
      failureCategory = null;
    } else if (failureCategory == null) {
      failureCategory = AiIntegrationFailureCategory.UNKNOWN;
    }
    emit("Analytics.Editor.IdeAiIntegrationAction", new AiIntegrationActionParams(params.getAction(), params.getStatus(), failureCategory, params.getAgent(), params.getHost()));
  }

  public void aiIntegrationCliStateObserved(@Nullable AiIntegrationCliStateObservedParams params) {
    if (params == null || params.getInstallationStatus() == null || params.getAuthenticationStatus() == null
      || params.getHost() == null) {
      return;
    }
    emit("Analytics.Editor.IdeAiIntegrationCliStateObserved", params);
  }

  public void aiAgentIntegrationStateObserved(@Nullable AiAgentIntegrationStateObservedParams params) {
    if (params == null || params.getAgent() == null || params.getStandaloneMcpState() == null
      || params.getHost() == null || params.getDetectionSources() == null || params.getDetectionSources().isEmpty()
      || params.getDetectionSources().stream().anyMatch(Objects::isNull)) {
      return;
    }
    // EnumSet iterates in AiAgentDetectionSource declaration order, not the order reported by the client.
    var sources = List.copyOf(EnumSet.copyOf(params.getDetectionSources()));
    emit("Analytics.Editor.IdeAiAgentIntegrationStateObserved", new AiAgentIntegrationStateObservedParams(params.getAgent(), sources,
      params.getStandaloneMcpState(), params.getHost()));
  }

  private void emit(String eventType, Object payload) {
    if (!isGessieFeatureEnabled || !userSetting.isTelemetryEnabledByUser()) {
      return;
    }
    try {
      client.postEvent(new GessieEvent(
        new GessieMetadata(UUID.randomUUID(),
          new GessieMetadata.GessieSource(SonarLintDomain.fromProductKey(telemetryConstantAttributes.getProductKey())),
          eventType,
          Long.toString(Instant.now().toEpochMilli()),
          "1"),
        payload));
    } catch (RuntimeException e) {
      // Do not log input or exception details, which may contain sensitive client data.
      LOG.debug("Failed to submit Gessie telemetry event");
    }
  }
}
