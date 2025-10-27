/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry;

import java.util.List;
import javax.annotation.Nullable;

/**
 * @param usesConnectedMode             At least one project in the IDE is bound to a SQ server or SC
 * @param usesSonarCloud                At least one project in the IDE is bound to SC
 * @param childBindingCount             Number of bindings for a child configuration scope
 * @param sonarQubeServerBindingCount   Number of bindings with SonarQube Server
 * @param sonarQubeCloudEUBindingCount  Number of bindings with SonarQube Cloud EU
 * @param sonarQubeCloudUSBindingCount  Number of bindings with SonarQube Cloud US
 * @param devNotificationsDisabled      Are dev notifications disabled (if multiple connections are configured, return true if feature is disabled for at least one connection)
 * @param nonDefaultEnabledRules        Rule keys for rules that disabled by default, but was enabled by user in settings.
 * @param defaultDisabledRules          Rule keys for rules that enabled by default, but was disabled by user in settings.
 * @param nodeVersion                   Node.js version used by analyzers (detected or configured by the user).
 *                                      Empty if no node present/detected/configured
 * @param connectionsAttributes         Information about the connections configured in the IDE
 */
public record TelemetryServerAttributes(boolean usesConnectedMode, boolean usesSonarCloud, int childBindingCount, int sonarQubeServerBindingCount,
                                        int sonarQubeCloudEUBindingCount, int sonarQubeCloudUSBindingCount, boolean devNotificationsDisabled,
                                        List<String> nonDefaultEnabledRules, List<String> defaultDisabledRules,
                                        @Nullable String nodeVersion, List<TelemetryConnectionAttributes> connectionsAttributes) {
}
