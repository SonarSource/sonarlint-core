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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects;

import com.google.gson.annotations.JsonAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherTransientConnectionAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;

public class GetAllProjectsParams {

  @JsonAdapter(EitherTransientConnectionAdapterFactory.class)
  private final Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection;

  public GetAllProjectsParams(Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> transientConnection) {
    this.transientConnection = transientConnection;
  }

  public GetAllProjectsParams(TransientSonarQubeConnectionDto transientConnection) {
    this(Either.forLeft(transientConnection));
  }

  public GetAllProjectsParams(TransientSonarCloudConnectionDto transientConnection) {
    this(Either.forRight(transientConnection));
  }

  public Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> getTransientConnection() {
    return transientConnection;
  }
}
