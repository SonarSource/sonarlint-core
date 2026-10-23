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

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

@JsonSegment("plugin")
public interface PluginRpcService {

  /**
   * Returns the status of all known analyzer plugins, including unsupported ones.
   *
   * <p>This method is intended for initial population of the "Supported Languages" panel.
   * The returned list contains one entry per language known to the backend, covering:
   * <ul>
   *   <li>the human-readable plugin/language name</li>
   *   <li>the current lifecycle state (active, synced, downloading, failed, premium, unsupported)</li>
   *   <li>the source of the plugin artifact (embedded, on-demand, SQS, SQC), if available</li>
   *   <li>the version currently in use, if the plugin is loaded</li>
   *   <li>the overridden version (a locally present version superseded by a sync), if applicable</li>
   * </ul>
   *
   * <p>When {@link GetPluginStatusesParams#getConfigurationScopeId()} is provided, the response reflects
   * the plugin landscape for the connection bound to that scope (connected mode).
   * When it is {@code null}, only embedded/standalone plugins are considered.
   */
  @JsonRequest
  CompletableFuture<GetPluginStatusesResponse> getPluginStatuses(GetPluginStatusesParams params);

}
