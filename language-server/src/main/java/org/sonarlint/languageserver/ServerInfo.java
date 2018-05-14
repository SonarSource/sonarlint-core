/*
 * SonarLint Language Server
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarlint.languageserver;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
class ServerInfo {
  final String serverId;
  final String serverUrl;
  final String token;

  @Nullable
  final String organizationKey;

  ServerInfo(String serverId, String serverUrl, String token, @Nullable String organizationKey) {
    this.serverId = serverId;
    this.serverUrl = serverUrl;
    this.token = token;
    this.organizationKey = organizationKey;
  }
}
