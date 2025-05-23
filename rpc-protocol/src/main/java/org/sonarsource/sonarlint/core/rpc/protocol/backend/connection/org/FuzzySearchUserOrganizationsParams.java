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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org;

import com.google.gson.annotations.JsonAdapter;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherCredentialsAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

public class FuzzySearchUserOrganizationsParams {

  @JsonAdapter(EitherCredentialsAdapterFactory.class)
  private final Either<TokenDto, UsernamePasswordDto> credentials;
  private final String searchText;
  private final SonarCloudRegion region;

  @Deprecated(since = "10.14")
  public FuzzySearchUserOrganizationsParams(Either<TokenDto, UsernamePasswordDto> credentials, String searchText) {
    this(credentials, searchText, SonarCloudRegion.EU);
  }

  public FuzzySearchUserOrganizationsParams(Either<TokenDto, UsernamePasswordDto> credentials, String searchText, SonarCloudRegion region) {
    this.credentials = credentials;
    this.searchText = searchText;
    this.region = region;
  }

  public Either<TokenDto, UsernamePasswordDto> getCredentials() {
    return credentials;
  }

  public String getSearchText() {
    return searchText;
  }

  public SonarCloudRegion getRegion() {
    return region;
  }
}
