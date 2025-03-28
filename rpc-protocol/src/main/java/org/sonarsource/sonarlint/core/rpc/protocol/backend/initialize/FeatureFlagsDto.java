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

/**
 * Optional features toggles of the backend. To ease transition or to accommodate different client needs.
 */
public class FeatureFlagsDto {

  private final boolean shouldManageSmartNotifications;
  @Deprecated
  // not controllable anymore, it is the backend's responsibility to decide
  private final boolean taintVulnerabilitiesEnabled;
  private final boolean shouldSynchronizeProjects;
  private final boolean shouldManageLocalServer;
  private final boolean enableSecurityHotspots;
  private final boolean shouldManageServerSentEvents;
  private final boolean enableDataflowBugDetection;
  private final boolean shouldManageFullSynchronization;
  private final boolean enableTelemetry;
  private final boolean canOpenFixSuggestion;
  private final boolean enableMonitoring;

  public FeatureFlagsDto(boolean shouldManageSmartNotifications, boolean taintVulnerabilitiesEnabled, boolean shouldSynchronizeProjects, boolean shouldManageLocalServer,
    boolean enableSecurityHotspots, boolean shouldManageServerSentEvents, boolean enableDataflowBugDetection, boolean shouldManageFullSynchronization, boolean enableTelemetry,
    boolean canOpenFixSuggestion, boolean enableMonitoring) {
    this.shouldManageSmartNotifications = shouldManageSmartNotifications;
    this.taintVulnerabilitiesEnabled = taintVulnerabilitiesEnabled;
    this.shouldSynchronizeProjects = shouldSynchronizeProjects;
    this.shouldManageLocalServer = shouldManageLocalServer;
    this.enableSecurityHotspots = enableSecurityHotspots;
    this.shouldManageServerSentEvents = shouldManageServerSentEvents;
    this.enableDataflowBugDetection = enableDataflowBugDetection;
    this.shouldManageFullSynchronization = shouldManageFullSynchronization;
    this.enableTelemetry = enableTelemetry;
    this.canOpenFixSuggestion = canOpenFixSuggestion;
    this.enableMonitoring = enableMonitoring;
  }

  public boolean shouldManageSmartNotifications() {
    return shouldManageSmartNotifications;
  }

  public boolean shouldManageServerSentEvents() {
    return shouldManageServerSentEvents;
  }

  /**
   * @deprecated not used anymore. It is the backend's responsibility to decide based on enabled languages
   * @return
   */
  @Deprecated
  public boolean areTaintVulnerabilitiesEnabled() {
    return taintVulnerabilitiesEnabled;
  }

  public boolean shouldSynchronizeProjects() {
    return shouldSynchronizeProjects;
  }

  public boolean shouldManageLocalServer() {
    return shouldManageLocalServer;
  }

  public boolean isEnableSecurityHotspots() {
    return enableSecurityHotspots;
  }

  public boolean isEnableDataflowBugDetection() {
    return enableDataflowBugDetection;
  }

  public boolean shouldManageFullSynchronization() {
    return shouldManageFullSynchronization;
  }

  public boolean isEnableTelemetry() {
    return enableTelemetry;
  }

  public boolean canOpenFixSuggestion() {
    return canOpenFixSuggestion;
  }

  public boolean isEnableMonitoring() {
    return enableMonitoring;
  }
}
