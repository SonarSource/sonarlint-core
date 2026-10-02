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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.ai;

import javax.annotation.Nullable;

public class CliIntegrationConfiguration {
  @Nullable
  private final String path;
  @Nullable
  private final CliIntegrationCheckStatus mcp;
  @Nullable
  private final CliIntegrationCheckStatus hooks;

  public CliIntegrationConfiguration(@Nullable String path, @Nullable CliIntegrationCheckStatus mcp,
    @Nullable CliIntegrationCheckStatus hooks) {
    this.path = path;
    this.mcp = mcp;
    this.hooks = hooks;
  }

  @Nullable
  public String getPath() {
    return path;
  }

  @Nullable
  public CliIntegrationCheckStatus getMcp() {
    return mcp;
  }

  @Nullable
  public CliIntegrationCheckStatus getHooks() {
    return hooks;
  }
}
