/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.connection;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;

public interface ConnectionManager {
  ServerConnectionWrapper getConnectionOrThrow(String connectionId);
  Optional<ServerConnectionWrapper> tryGetConnection(String connectionId);
  ServerApi getTransientConnection(String token, @Nullable String organization, String baseUrl); // or return TransientConnection
  Optional<ServerConnectionWrapper> getValidConnection(String connectionId);
  void withValidConnection(String connectionId, Consumer<ServerConnectionWrapper> serverConnectionCall);
  <T> Optional<T> withValidConnectionAndReturn(String connectionId, Function<ServerConnectionWrapper, Optional<T>> serverConnectionCall);
}
