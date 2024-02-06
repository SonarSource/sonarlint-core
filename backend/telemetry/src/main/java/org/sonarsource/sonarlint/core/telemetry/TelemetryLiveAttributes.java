/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2024 SonarSource SA
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
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;

public class TelemetryLiveAttributes {

  private final TelemetryServerLiveAttributes serverAttributes;
  private final TelemetryClientLiveAttributesResponse clientAttributes;

  public TelemetryLiveAttributes(TelemetryServerLiveAttributes serverAttributes,
    TelemetryClientLiveAttributesResponse clientAttributes) {
    this.serverAttributes = serverAttributes;
    this.clientAttributes = clientAttributes;
  }

  public boolean usesConnectedMode() {
    return serverAttributes.usesConnectedMode();
  }

  public boolean usesSonarCloud() {
    return serverAttributes.usesSonarCloud();
  }

  public boolean isDevNotificationsDisabled() {
    return serverAttributes.isDevNotificationsDisabled();
  }

  public List<String> getNonDefaultEnabledRules() {
    return serverAttributes.getNonDefaultEnabledRules();
  }

  public List<String> getDefaultDisabledRules() {
    return serverAttributes.getDefaultDisabledRules();
  }

  @Nullable
  public String getNodeVersion() {
    return serverAttributes.getNodeVersion();
  }

  public Map<String, Object> getAdditionalAttributes() {
    return clientAttributes.getAdditionalAttributes();
  }
}
