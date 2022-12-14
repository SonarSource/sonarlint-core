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
package org.sonarsource.sonarlint.core.clientapi.backend;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

/**
 * The data from this class will be used outside the IDE, e.g. for the sonarlint/api/status endpoint or when opening the page to generate the user token
 */
public class ClientInfoDto {
  private final String name;
  private final String version;
  private final String edition;

  public ClientInfoDto(@NonNull String name, @NonNull String version, @Nullable String edition) {
    this.name = name;
    this.version = version;
    this.edition = edition;
  }

  @NonNull
  public String getName() {
    return name;
  }

  @NonNull
  public String getVersion() {
    return version;
  }

  @CheckForNull
  public String getEdition() {
    return edition;
  }
}
