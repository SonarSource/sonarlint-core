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
package org.sonarsource.sonarlint.core.rpc.protocol.client.message;

import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

/**
 * The one-time message should be displayed as a warning containing the following elements:
 * <ul>
 *   <li>A unique ID representing a pair of connection ID + version used by the client to remember that this specific notification has been already seen</li>
 *   <li>A configuration scope ID to know where the notification should be displayed</li>
 *   <li>The text to be displayed</li>
 * </ul>
 */
public class ShowSoonUnsupportedMessageParams {

  @NonNull
  private final String doNotShowAgainId;
  @NonNull
  private final String configurationScopeId;
  @NonNull
  private final String text;

  public ShowSoonUnsupportedMessageParams(@NonNull String doNotShowAgainId, @NonNull String configurationScopeId, @NonNull String text) {
    this.doNotShowAgainId = doNotShowAgainId;
    this.configurationScopeId = configurationScopeId;
    this.text = text;
  }

  public String getDoNotShowAgainId() {
    return doNotShowAgainId;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public String getText() {
    return text;
  }
}
