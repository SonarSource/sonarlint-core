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

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;

public class SonarCloudConnectionParams {
  private final String organizationKey;
  private final String tokenName;
  private final String tokenValue;
  private final SonarCloudRegion region;

  public SonarCloudConnectionParams(String organizationKey, @Nullable String tokenName, @Nullable String tokenValue, SonarCloudRegion region) {
    this.organizationKey = organizationKey;
    this.tokenName = tokenName;
    this.tokenValue = tokenValue;
    this.region = region;
  }

  public String getOrganizationKey() {
    return organizationKey;
  }

  @CheckForNull
  public String getTokenName() {
    return tokenName;
  }

  @CheckForNull
  public String getTokenValue() {
    return tokenValue;
  }

  public SonarCloudRegion getRegion() {
    return region;
  }
}
