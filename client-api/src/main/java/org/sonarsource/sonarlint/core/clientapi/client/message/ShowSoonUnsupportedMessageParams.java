/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.client.message;

import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

/**
 * The one-time message contains the following elements:
 * <ul>
 *   <li>A notification ID unique per pair of connection ID + version used by the client to remember that this specific notification has been already seen</li>
 *   <li>A configuration scope ID to know where the notification should be displayed</li>
 *   <li>A message type</li>
 *   <li>The text to be displayed</li>
 * </ul>
 * The type can be one of:
 * <ul>
 *   <li>"ERROR"</li>
 *   <li>"WARNING"</li>
 *   <li>"INFO"</li>
 * </ul>
 */
public class ShowSoonUnsupportedMessageParams {

  @NonNull
  private final String notificationId;
  @NonNull
  private final String configurationScopeId;
  @NonNull
  private final MessageType type;
  @NonNull
  private final String text;

  public ShowSoonUnsupportedMessageParams(@NonNull String notificationId, @NonNull String configurationScopeId, @NonNull MessageType type, @NonNull String text) {
    this.notificationId = notificationId;
    this.configurationScopeId = configurationScopeId;
    this.type = type;
    this.text = text;
  }

  public String getNotificationId() {
    return notificationId;
  }

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }

  public String getType() {
    return type.name();
  }

  public String getText() {
    return text;
  }
}
