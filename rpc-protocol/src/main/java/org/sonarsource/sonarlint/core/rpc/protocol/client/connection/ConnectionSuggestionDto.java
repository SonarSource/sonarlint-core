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
package org.sonarsource.sonarlint.core.rpc.protocol.client.connection;

import com.google.gson.annotations.JsonAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherSonarQubeSonarCloudConnectionAdapterFactory;

public class ConnectionSuggestionDto {

  @JsonAdapter(EitherSonarQubeSonarCloudConnectionAdapterFactory.class)
  private final Either<SonarQubeConnectionSuggestionDto, SonarCloudConnectionSuggestionDto> connectionSuggestion;

  public ConnectionSuggestionDto(Either<SonarQubeConnectionSuggestionDto, SonarCloudConnectionSuggestionDto> connectionSuggestion) {
    this.connectionSuggestion = connectionSuggestion;
  }

  public ConnectionSuggestionDto(SonarQubeConnectionSuggestionDto connection) {
    this(Either.forLeft(connection));
  }

  public ConnectionSuggestionDto(SonarCloudConnectionSuggestionDto connection) {
    this(Either.forRight(connection));
  }

  public Either<SonarQubeConnectionSuggestionDto, SonarCloudConnectionSuggestionDto> getConnectionSuggestion() {
    return connectionSuggestion;
  }

}
