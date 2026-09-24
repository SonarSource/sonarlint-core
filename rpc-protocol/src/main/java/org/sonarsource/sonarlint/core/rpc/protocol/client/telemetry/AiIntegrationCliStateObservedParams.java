/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry;

import com.google.gson.annotations.JsonAdapter;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.StrictBooleanTypeAdapter;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;

public class AiIntegrationCliStateObservedParams {
  private final AiIntegrationObservationTrigger trigger;
  private final CliInstallationStatus installationStatus;
  private final CliAuthenticationStatus authenticationStatus;
  @Nullable
  @JsonAdapter(StrictBooleanTypeAdapter.class)
  private final Boolean vortexAvailable;
  private final AiIntegrationHost host;
  private final AiIntegrationEnvironment environment;

  public AiIntegrationCliStateObservedParams(AiIntegrationObservationTrigger trigger,
    CliInstallationStatus installationStatus,
    CliAuthenticationStatus authenticationStatus,
    @Nullable Boolean vortexAvailable,
    AiIntegrationHost host,
    AiIntegrationEnvironment environment) {
    this.trigger = trigger;
    this.installationStatus = installationStatus;
    this.authenticationStatus = authenticationStatus;
    this.vortexAvailable = vortexAvailable;
    this.host = host;
    this.environment = environment;
  }

  public AiIntegrationObservationTrigger getTrigger() {
    return trigger;
  }

  public CliInstallationStatus getInstallationStatus() {
    return installationStatus;
  }

  public CliAuthenticationStatus getAuthenticationStatus() {
    return authenticationStatus;
  }

  @Nullable
  public Boolean getVortexAvailable() {
    return vortexAvailable;
  }

  public AiIntegrationHost getHost() {
    return host;
  }

  public AiIntegrationEnvironment getEnvironment() {
    return environment;
  }
}
