/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry;

import java.util.List;
import javax.annotation.Nullable;

public class TelemetryServerLiveAttributes {
  /**
   * At least one project in the IDE is bound to a SQ server or SC
   */
  private final boolean usesConnectedMode;

  /**
   * At least one project in the IDE is bound to SC
   */
  private final boolean usesSonarCloud;

  /**
   * Are dev notifications disabled (if multiple connections are configured, return true if feature is disabled for at least one connection)
   */
  private final boolean devNotificationsDisabled;

  /**
   * Rule keys for rules that disabled by default, but was enabled by user in settings.
   */
  private final List<String> nonDefaultEnabledRules;

  /**
   * Rule keys for rules that enabled by default, but was disabled by user in settings.
   */
  private final List<String> defaultDisabledRules;

  /**
   * Node.js version used by analyzers (detected or configured by the user).
   * Empty if no node present/detected/configured
   */
  @Nullable
  private final String nodeVersion;

  public TelemetryServerLiveAttributes(boolean usesConnectedMode, boolean usesSonarCloud, boolean devNotificationsDisabled,
    List<String> nonDefaultEnabledRules, List<String> defaultDisabledRules, @Nullable String nodeVersion) {
    this.usesConnectedMode = usesConnectedMode;
    this.usesSonarCloud = usesSonarCloud;
    this.devNotificationsDisabled = devNotificationsDisabled;
    this.nonDefaultEnabledRules = nonDefaultEnabledRules;
    this.defaultDisabledRules = defaultDisabledRules;
    this.nodeVersion = nodeVersion;
  }

  public boolean usesConnectedMode() {
    return usesConnectedMode;
  }

  public boolean usesSonarCloud() {
    return usesSonarCloud;
  }

  public boolean isDevNotificationsDisabled() {
    return devNotificationsDisabled;
  }

  public List<String> getNonDefaultEnabledRules() {
    return nonDefaultEnabledRules;
  }

  public List<String> getDefaultDisabledRules() {
    return defaultDisabledRules;
  }

  @Nullable
  public String getNodeVersion() {
    return nodeVersion;
  }
}
