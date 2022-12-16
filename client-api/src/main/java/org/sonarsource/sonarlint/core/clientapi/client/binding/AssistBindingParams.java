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
package org.sonarsource.sonarlint.core.clientapi.client.binding;

import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class AssistBindingParams {
  private final Either<ExistingConnection, UnknownServer> connection;
  private final String projectKey;

  public AssistBindingParams(Either<ExistingConnection, UnknownServer> connection, String projectKey) {
    this.connection = connection;
    this.projectKey = projectKey;
  }

  public Either<ExistingConnection, UnknownServer> getConnection() {
    return connection;
  }

  public String getProjectKey() {
    return projectKey;
  }

  public static class ExistingConnection {
    private final String id;

    public ExistingConnection(@NonNull String id) {
      this.id = id;
    }

    @NonNull
    public String getId() {
      return id;
    }
  }

  public static class UnknownServer {
    private final String url;

    public UnknownServer(@NonNull String url) {
      this.url = url;
    }

    @NonNull
    public String getUrl() {
      return url;
    }
  }
}
