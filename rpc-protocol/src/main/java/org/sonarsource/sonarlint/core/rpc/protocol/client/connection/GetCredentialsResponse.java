/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.connection;

import com.google.gson.annotations.JsonAdapter;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherCredentialsAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

public class GetCredentialsResponse {

  @JsonAdapter(EitherCredentialsAdapterFactory.class)
  @Nullable
  private final Either<TokenDto, UsernamePasswordDto> credentials;

  public GetCredentialsResponse(@Nullable Either<TokenDto, UsernamePasswordDto> credentials) {
    this.credentials = credentials;
  }

  public GetCredentialsResponse(TokenDto token) {
    this(Either.forLeft(token));
  }

  public GetCredentialsResponse(UsernamePasswordDto usernamePassword) {
    this(Either.forRight(usernamePassword));
  }

  /**
   * @return @{@code null} if no credentials defined for this connection
   */
  @CheckForNull
  public Either<TokenDto, UsernamePasswordDto> getCredentials() {
    return credentials;
  }
}
