/*
 * SonarLint Core - Client API
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.clientapi.connection.config;

import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class DidUpdateConnectionParams {

  private final Either<SonarQubeConnectionConfiguration, SonarCloudConnectionConfiguration> updatedConnection;

  public DidUpdateConnectionParams(Either<SonarQubeConnectionConfiguration, SonarCloudConnectionConfiguration> updatedConnection) {
    this.updatedConnection = updatedConnection;
  }

  public DidUpdateConnectionParams(SonarQubeConnectionConfiguration updatedConnection) {
    this.updatedConnection = Either.forLeft(updatedConnection);
  }

  public DidUpdateConnectionParams(SonarCloudConnectionConfiguration updatedConnection) {
    this.updatedConnection = Either.forRight(updatedConnection);
  }

  public Either<SonarQubeConnectionConfiguration, SonarCloudConnectionConfiguration> getUpdatedConnection() {
    return updatedConnection;
  }
}
