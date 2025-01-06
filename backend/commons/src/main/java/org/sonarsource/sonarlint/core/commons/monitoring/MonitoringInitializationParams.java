/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.monitoring;

public class MonitoringInitializationParams {
  private final boolean enabled;
  private final String productKey;
  private final String sonarQubeForIdeVersion;
  private final String ideVersion;

  public MonitoringInitializationParams(boolean enabled, String productKey, String sonarQubeForIdeVersion, String ideVersion) {
    this.enabled = enabled;
    this.productKey = productKey;
    this.sonarQubeForIdeVersion = sonarQubeForIdeVersion;
    this.ideVersion = ideVersion;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getProductKey() {
    return productKey;
  }

  public String getSonarQubeForIdeVersion() {
    return sonarQubeForIdeVersion;
  }

  public String getIdeVersion() {
    return ideVersion;
  }
}
