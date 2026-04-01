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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin;

import java.util.List;

/**
 * Response to {@link PluginRpcService#getPluginStatuses(GetPluginStatusesParams)}.
 */
public class GetPluginStatusesResponse {

  private final List<PluginStatusDto> pluginStatuses;

  /**
   * @param pluginStatuses the full list of analyzer plugin statuses, one entry per language
   *                       known to the backend (including unsupported ones)
   */
  public GetPluginStatusesResponse(List<PluginStatusDto> pluginStatuses) {
    this.pluginStatuses = pluginStatuses;
  }

  public List<PluginStatusDto> getPluginStatuses() {
    return pluginStatuses;
  }
}
