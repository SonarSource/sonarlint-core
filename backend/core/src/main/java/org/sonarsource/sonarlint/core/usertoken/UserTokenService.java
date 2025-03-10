/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.usertoken;

import org.sonarsource.sonarlint.core.ConnectionManager;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.RevokeTokenParams;

public class UserTokenService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConnectionManager connectionManager;

  public UserTokenService(ConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }

  public void revokeToken(RevokeTokenParams params, SonarLintCancelMonitor cancelMonitor) {
    LOG.debug(String.format("Revoking token '%s'", params.getTokenName()));
    connectionManager.getServerApi(params.getBaseUrl(), null, params.getTokenValue())
      .userTokens()
      .revoke(params.getTokenName(), cancelMonitor);
  }

}
