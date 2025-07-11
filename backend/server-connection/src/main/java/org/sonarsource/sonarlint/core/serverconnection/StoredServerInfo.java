/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.sonarsource.sonarlint.core.commons.Version;

import static org.sonarsource.sonarlint.core.serverconnection.ServerSettings.MQR_MODE_SETTING;

public record StoredServerInfo(Version version, ServerSettings globalSettings) {
  private static final String MIN_MQR_MODE_SUPPORT_VERSION = "10.2";
  private static final String MQR_MODE_SETTING_MIN_VERSION = "10.8";

  public boolean shouldConsiderMultiQualityModeEnabled() {
    if (version.satisfiesMinRequirement(Version.create(MQR_MODE_SETTING_MIN_VERSION))) {
      // starting 10.8, the sonar.multi-quality-mode.enabled setting was introduced. We honor this setting in priority
      return globalSettings.getAsBoolean(MQR_MODE_SETTING).orElse(false);
    }
    // if no setting is present, MQR mode should be used for 10.2+, otherwise standard mode should be used
    return version.satisfiesMinRequirement(Version.create(MIN_MQR_MODE_SUPPORT_VERSION));
  }
}
