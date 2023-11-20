/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize;

import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryConstantAttributesDto;

/**
 * Static information to describe the client. Dynamic information will be provided when needed by calling {@link SonarLintRpcClient#getClientDescription()}
 */
public class ClientInfoDto {
  /**
   * Name of the client, that could be used outside the IDE, e.g. for the sonarlint/api/status endpoint or when opening the page to generate the user token
   */
  private final String name;

  /**
   * User agent used for all HTTP requests made by the backend
   */
  private final String userAgent;

  /**
   * Static information about the client, that will be sent with the telemetry payload
   */
  private final TelemetryConstantAttributesDto telemetryConstantAttributesDto;

  public ClientInfoDto(@NonNull String name, @NonNull String userAgent, TelemetryConstantAttributesDto telemetryConstantAttributesDto) {
    this.name = name;
    this.userAgent = userAgent;
    this.telemetryConstantAttributesDto = telemetryConstantAttributesDto;
  }

  @NonNull
  public String getName() {
    return name;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public TelemetryConstantAttributesDto getTelemetryInitDto() {
    return telemetryConstantAttributesDto;
  }
}
