/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.http;

import javax.annotation.Nullable;

public interface HttpConnectionListener {
  /**
   * Should be called when the request returns status >= 200 and < 300.
   */
  void onConnected();

  /**
   * Should be called when the request returns status < 200 or >= 300, or another error occurs. No need to call {@link #onClosed()} after that.
   * @param responseCode the HTTP status response, or null for other error types (e.g. timeout)
   */
  void onError(@Nullable Integer responseCode);

  /**
   * Should be called when the connection is closed, only after it was successfully established (ie after {@link #onConnected()} was called)
   */
  void onClosed();
}
