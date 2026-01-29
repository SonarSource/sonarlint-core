/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class ShowMessageRequestResponse {

  // Selected key can be null in case user explicitly dismisses the notification or does not act on it at all
  @Nullable
  private final String selectedKey;

  // Depicts if user explicitly closed the notification or not (e.g. by clicking X). If true, selectedKey should be null.
  private final boolean closedByUser;

  public ShowMessageRequestResponse(@Nullable String selectedKey, boolean closedByUser) {
    this.selectedKey = selectedKey;
    this.closedByUser = closedByUser;
  }

  @CheckForNull
  public String getSelectedKey() {
    return selectedKey;
  }

  public boolean isClosedByUser() {
    return closedByUser;
  }
}
