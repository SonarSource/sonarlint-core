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

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Optional features toggles of the backend. To ease transition or to accommodate different client needs.
 */
public class FeatureFlagsDto {

  private final Set<FeatureFlag> featureFlags = EnumSet.noneOf(FeatureFlag.class);

  public FeatureFlagsDto(Collection<FeatureFlag> featureFlags) {
    this.featureFlags.addAll(featureFlags);
  }

  /**
   * @deprecated call new constructor accepting enums
   *
   */
  @Deprecated(since = "10.19")
  public FeatureFlagsDto(boolean shouldManageSmartNotifications, boolean taintVulnerabilitiesEnabled, boolean shouldSynchronizeProjects, boolean shouldManageLocalServer,
    boolean enableSecurityHotspots, boolean shouldManageServerSentEvents, boolean enableDataflowBugDetection, boolean shouldManageFullSynchronization, boolean enableTelemetry,
    boolean canOpenFixSuggestion, boolean enableMonitoring) {
    addIfTrue(shouldManageSmartNotifications, FeatureFlag.SHOULD_MANAGE_SMART_NOTIFICATIONS);
    addIfTrue(taintVulnerabilitiesEnabled, FeatureFlag.TAINT_VULNERABILITIES_ENABLED);
    addIfTrue(shouldSynchronizeProjects, FeatureFlag.SHOULD_SYNCHRONIZE_PROJECTS);
    addIfTrue(shouldManageLocalServer, FeatureFlag.SHOULD_MANAGE_LOCAL_SERVER);
    addIfTrue(enableSecurityHotspots, FeatureFlag.ENABLE_SECURITY_HOTSPOTS);
    addIfTrue(shouldManageServerSentEvents, FeatureFlag.SHOULD_MANAGE_SERVER_SENT_EVENTS);
    addIfTrue(enableDataflowBugDetection, FeatureFlag.ENABLE_DATAFLOW_BUG_DETECTION);
    addIfTrue(shouldManageFullSynchronization, FeatureFlag.SHOULD_MANAGE_FULL_SYNCHRONIZATION);
    addIfTrue(enableTelemetry, FeatureFlag.ENABLE_TELEMETRY);
    addIfTrue(canOpenFixSuggestion, FeatureFlag.CAN_OPEN_FIX_SUGGESTION);
    addIfTrue(enableMonitoring, FeatureFlag.ENABLE_MONITORING);
  }

  private void addIfTrue(boolean enabled, FeatureFlag featureFlag) {
    if (enabled) {
      featureFlags.add(featureFlag);
    }
  }

  public boolean shouldManageSmartNotifications() {
    return featureFlags.contains(FeatureFlag.SHOULD_MANAGE_SMART_NOTIFICATIONS);
  }

  public boolean shouldManageServerSentEvents() {
    return featureFlags.contains(FeatureFlag.SHOULD_MANAGE_SERVER_SENT_EVENTS);
  }

  /**
   * @deprecated not used anymore. It is the backend's responsibility to decide based on enabled languages
   * @return
   */
  @Deprecated
  public boolean areTaintVulnerabilitiesEnabled() {
    return featureFlags.contains(FeatureFlag.TAINT_VULNERABILITIES_ENABLED);
  }

  public boolean shouldSynchronizeProjects() {
    return featureFlags.contains(FeatureFlag.SHOULD_SYNCHRONIZE_PROJECTS);
  }

  public boolean shouldManageLocalServer() {
    return featureFlags.contains(FeatureFlag.SHOULD_MANAGE_LOCAL_SERVER);
  }

  public boolean isEnableSecurityHotspots() {
    return featureFlags.contains(FeatureFlag.ENABLE_SECURITY_HOTSPOTS);
  }

  public boolean isEnableDataflowBugDetection() {
    return featureFlags.contains(FeatureFlag.ENABLE_DATAFLOW_BUG_DETECTION);
  }

  public boolean shouldManageFullSynchronization() {
    return featureFlags.contains(FeatureFlag.SHOULD_MANAGE_FULL_SYNCHRONIZATION);
  }

  public boolean isEnableTelemetry() {
    return featureFlags.contains(FeatureFlag.ENABLE_TELEMETRY);
  }

  public boolean canOpenFixSuggestion() {
    return featureFlags.contains(FeatureFlag.CAN_OPEN_FIX_SUGGESTION);
  }

  public boolean isEnableMonitoring() {
    return featureFlags.contains(FeatureFlag.ENABLE_MONITORING);
  }
}
