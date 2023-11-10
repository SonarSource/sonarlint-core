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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Telemetry attributes provided by the client (IDE) at the time the telemetry ping is sent.
 */
public interface TelemetryClientAttributesProvider {

  /**
   * At least one project in the IDE is bound to a SQ server or SC
   */
  boolean usesConnectedMode();

  /**
   * At least one project in the IDE is bound to SC
   */
  boolean useSonarCloud();

  /**
   * Node.js version used by analyzers (detected or configured by the user).
   * @return empty if no node present/detected/configured
   */
  Optional<String> nodeVersion();

  /**
   * Are dev notifications disabled (if multiple connections are configured, return true if feature is disabled for at least one connection)
   */
  boolean devNotificationsDisabled();

  /**
   * Rule keys for rules that disabled by default, but was enabled by user in settings.
   */
  Collection<String> getNonDefaultEnabledRules();

  /**
   * Rule keys for rules that enabled by default, but was disabled by user in settings.
   */
  Collection<String> getDefaultDisabledRules();

  /**
   * Map of additional attributes to be passed to the telemetry. Values types can be {@link String}, {@link Boolean} or {@link Number}. You can also pass a Map for nested objects.
   */
  Map<String, Object> additionalAttributes();

}
