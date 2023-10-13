/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification;

import java.util.Set;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class ShowSmartNotificationParams {

  @NonNull
  private final String category;
  @NonNull
  private final String connectionId;
  @NonNull
  private final String link;
  @NonNull
  private final Set<String> scopeIds;
  @NonNull
  private final String text;

  public ShowSmartNotificationParams(@NonNull String text, @NonNull String link,
    @NonNull Set<String> scopeIds, @NonNull String category, @NonNull String connectionId) {
    this.text = text;
    this.link = link;
    this.scopeIds = scopeIds;
    this.category = category;
    this.connectionId = connectionId;
  }

  public String getCategory() {
    return category;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public String getLink() {
    return link;
  }

  public Set<String> getScopeIds() {
    return scopeIds;
  }

  public String getText() {
    return text;
  }

}
