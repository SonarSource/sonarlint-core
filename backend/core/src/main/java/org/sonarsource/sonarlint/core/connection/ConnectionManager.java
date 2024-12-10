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
package org.sonarsource.sonarlint.core.connection;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;

public interface ConnectionManager {
  ServerConnection getConnectionOrThrow(String connectionId);
  /**
   *   Having dedicated TransientConnection class makes sense only if we handle the connection errors from there.
   *   Which brings up the problem of managing global state for notifications because we don't know the connection ID. <br/><br/>
   *   On other hand providing ServerApis directly, all Web API calls from transient ServerApi are not protected by checks for connection state.
   *   So we still can spam server with unprotected requests.
   *   It's not a big problem because we don't use such requests during scheduled sync.
   *   They are mostly related to setting up the connection or other user-triggered actions.
   */
  ServerApi getTransientConnection(String token, @Nullable String organization, String baseUrl);
  void withValidConnection(String connectionId, Consumer<ServerApi> serverApiConsumer);
  <T> Optional<T> withValidConnectionAndReturn(String connectionId, Function<ServerApi, T> serverApiConsumer);
  <T> Optional<T> withValidConnectionFlatMapOptionalAndReturn(String connectionId, Function<ServerApi, Optional<T>> serverApiConsumer);
}
