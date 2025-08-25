/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.connection;

import com.google.gson.annotations.JsonAdapter;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherSonarQubeSonarCloudConnectionAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

public class ConnectionSuggestionDto {

  @JsonAdapter(EitherSonarQubeSonarCloudConnectionAdapterFactory.class)
  private final Either<SonarQubeConnectionSuggestionDto, SonarCloudConnectionSuggestionDto> connectionSuggestion;
  @Deprecated(forRemoval = true)
  private final boolean isFromSharedConfiguration;

  private final BindingSuggestionOrigin origin;

  public ConnectionSuggestionDto(Either<SonarQubeConnectionSuggestionDto, SonarCloudConnectionSuggestionDto> connectionSuggestion,
    BindingSuggestionOrigin origin) {
    this.connectionSuggestion = connectionSuggestion;
    this.isFromSharedConfiguration = origin == BindingSuggestionOrigin.SHARED_CONFIGURATION;
    this.origin = origin;
  }

  public ConnectionSuggestionDto(SonarQubeConnectionSuggestionDto connection, BindingSuggestionOrigin origin) {
    this(Either.forLeft(connection), origin);
  }

  public ConnectionSuggestionDto(SonarCloudConnectionSuggestionDto connection, BindingSuggestionOrigin origin) {
    this(Either.forRight(connection), origin);
  }

  public Either<SonarQubeConnectionSuggestionDto, SonarCloudConnectionSuggestionDto> getConnectionSuggestion() {
    return connectionSuggestion;
  }

  /**
   * @deprecated avoid calling this method if possible, since it will be removed once all the clients are migrated.
   * Rely on {@link #getOrigin()}  instead.
   */
  @Deprecated(forRemoval = true)
  public boolean isFromSharedConfiguration() {
    return isFromSharedConfiguration;
  }

  public BindingSuggestionOrigin getOrigin() {
    return origin;
  }
}
