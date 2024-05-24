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
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherSonarQubeSonarCloudConnectionParamsAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

public class AssistCreatingConnectionParams {
  @JsonAdapter(EitherSonarQubeSonarCloudConnectionParamsAdapterFactory.class)
  private final Either<SonarQubeConnectionParams, SonarCloudConnectionParams> connectionParams;

  public AssistCreatingConnectionParams(Either<SonarQubeConnectionParams, SonarCloudConnectionParams> connectionParams) {
    this.connectionParams = connectionParams;
  }

  public AssistCreatingConnectionParams(SonarQubeConnectionParams sonarQubeConnection) {
    this(Either.forLeft(sonarQubeConnection));
  }

  public AssistCreatingConnectionParams(SonarCloudConnectionParams sonarCloudConnection) {
    this(Either.forRight(sonarCloudConnection));
  }

  public Either<SonarQubeConnectionParams, SonarCloudConnectionParams> getConnectionParams() {
    return connectionParams;
  }

  /**
   * @deprecated Use {@link #getConnectionParams()}.getLeft().getServerUrl() instead.
   */
  @Deprecated(since = "10.3", forRemoval = true)
  public String getServerUrl() {
    return connectionParams.isLeft() ? connectionParams.getLeft().getServerUrl() : null;
  }

  public String getTokenName() {
    return connectionParams.isLeft() ?
      connectionParams.getLeft().getTokenName()
      : connectionParams.getRight().getTokenName();
  }

  public String getTokenValue() {
    return connectionParams.isLeft() ?
      connectionParams.getLeft().getTokenValue()
      : connectionParams.getRight().getTokenValue();
  }
}
