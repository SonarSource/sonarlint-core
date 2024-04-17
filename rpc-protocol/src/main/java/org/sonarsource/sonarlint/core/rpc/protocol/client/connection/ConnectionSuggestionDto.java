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
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherSonarQubeSonarCloudConnectionAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

public class ConnectionSuggestionDto {

  @JsonAdapter(EitherSonarQubeSonarCloudConnectionAdapterFactory.class)
  private final Either<SonarQubeConnectionSuggestionDto, SonarCloudConnectionSuggestionDto> connectionSuggestion;
  private final boolean isFromSharedConfiguration;

  public ConnectionSuggestionDto(Either<SonarQubeConnectionSuggestionDto, SonarCloudConnectionSuggestionDto> connectionSuggestion,
    boolean isFromSharedConfiguration) {
    this.connectionSuggestion = connectionSuggestion;
    this.isFromSharedConfiguration = isFromSharedConfiguration;
  }

  public ConnectionSuggestionDto(SonarQubeConnectionSuggestionDto connection, boolean isFromSharedConfiguration) {
    this(Either.forLeft(connection), isFromSharedConfiguration);
  }

  public ConnectionSuggestionDto(SonarCloudConnectionSuggestionDto connection, boolean isFromSharedConfiguration) {
    this(Either.forRight(connection), isFromSharedConfiguration);
  }

  public Either<SonarQubeConnectionSuggestionDto, SonarCloudConnectionSuggestionDto> getConnectionSuggestion() {
    return connectionSuggestion;
  }

  public boolean isFromSharedConfiguration() {
    return isFromSharedConfiguration;
  }

}
