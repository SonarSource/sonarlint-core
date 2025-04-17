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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize;

import java.util.EnumSet;
import java.util.Set;

import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.DATAFLOW_BUG_DETECTION;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.EMBEDDED_SERVER;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.FULL_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.MONITORING;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.PROJECT_SYNCHRONIZATION;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SECURITY_HOTSPOTS;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SERVER_SENT_EVENTS;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SMART_NOTIFICATIONS;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.TELEMETRY;

/**
 * @deprecated use InitializeParams constructor that accepts Set<BackendCapability> directly
 * Optional features toggles of the backend. To ease transition or to accommodate different client needs.
 */
@Deprecated(since = "10.19", forRemoval = true)
public class FeatureFlagsDto {

  private final Set<BackendCapability> backendCapabilities = EnumSet.noneOf(BackendCapability.class);

  /**
   * @deprecated call new constructor accepting enums
   *
   */
  @Deprecated(since = "10.19")
  public FeatureFlagsDto(boolean shouldManageSmartNotifications, boolean taintVulnerabilitiesEnabled, boolean shouldSynchronizeProjects, boolean shouldManageLocalServer,
    boolean enableSecurityHotspots, boolean shouldManageServerSentEvents, boolean enableDataflowBugDetection, boolean shouldManageFullSynchronization, boolean enableTelemetry,
    boolean canOpenFixSuggestion, boolean enableMonitoring) {
    addIfTrue(shouldManageSmartNotifications, SMART_NOTIFICATIONS);
    addIfTrue(shouldSynchronizeProjects, PROJECT_SYNCHRONIZATION);
    addIfTrue(shouldManageLocalServer, EMBEDDED_SERVER);
    addIfTrue(enableSecurityHotspots, SECURITY_HOTSPOTS);
    addIfTrue(shouldManageServerSentEvents, SERVER_SENT_EVENTS);
    addIfTrue(enableDataflowBugDetection, DATAFLOW_BUG_DETECTION);
    addIfTrue(shouldManageFullSynchronization, FULL_SYNCHRONIZATION);
    addIfTrue(enableTelemetry, TELEMETRY);
    addIfTrue(enableMonitoring, MONITORING);
  }

  private void addIfTrue(boolean enabled, BackendCapability backendCapability) {
    if (enabled) {
      backendCapabilities.add(backendCapability);
    }
  }

  public boolean shouldManageSmartNotifications() {
    return backendCapabilities.contains(SMART_NOTIFICATIONS);
  }

  public boolean shouldManageServerSentEvents() {
    return backendCapabilities.contains(SERVER_SENT_EVENTS);
  }

  public boolean shouldSynchronizeProjects() {
    return backendCapabilities.contains(PROJECT_SYNCHRONIZATION);
  }

  public boolean shouldManageLocalServer() {
    return backendCapabilities.contains(EMBEDDED_SERVER);
  }

  public boolean isEnablesSecurityHotspots() {
    return backendCapabilities.contains(SECURITY_HOTSPOTS);
  }

  public boolean isEnabledDataflowBugDetection() {
    return backendCapabilities.contains(DATAFLOW_BUG_DETECTION);
  }

  public boolean shouldManageFullSynchronization() {
    return backendCapabilities.contains(FULL_SYNCHRONIZATION);
  }

  public boolean isEnabledTelemetry() {
    return backendCapabilities.contains(TELEMETRY);
  }

  public boolean isEnabledMonitoring() {
    return backendCapabilities.contains(MONITORING);
  }
}
