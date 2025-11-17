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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org;

import com.google.gson.annotations.JsonAdapter;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherCredentialsAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

public class GetOrganizationParams {

  @JsonAdapter(EitherCredentialsAdapterFactory.class)
  private final Either<TokenDto, UsernamePasswordDto> credentials;
  private final String organizationKey;
  private final SonarCloudRegion region;

  public GetOrganizationParams(Either<TokenDto, UsernamePasswordDto> credentials, String organizationKey, SonarCloudRegion region) {
    this.credentials = credentials;
    this.organizationKey = organizationKey;
    this.region = region;
  }

  public String getOrganizationKey() {
    return organizationKey;
  }

  public Either<TokenDto, UsernamePasswordDto> getCredentials() {
    return credentials;
  }

  public SonarCloudRegion getRegion() {
    return region;
  }
}
