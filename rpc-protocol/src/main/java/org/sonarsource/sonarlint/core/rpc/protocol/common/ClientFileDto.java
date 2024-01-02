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
package org.sonarsource.sonarlint.core.rpc.protocol.common;

import java.net.URI;
import java.nio.file.Path;
import javax.annotation.Nullable;

public class ClientFileDto {

  private final URI uri;
  private final Path ideRelativePath;
  private final String configScopeId;
  @Nullable
  private final Boolean isTest;
  @Nullable
  private final String charset;
  @Nullable
  private final Path fsPath;
  @Nullable
  private final String content;

  public ClientFileDto(URI uri, Path relativePath, String configScopeId, @Nullable Boolean isTest, @Nullable String charset, @Nullable Path fsPath, @Nullable String content) {
    this.uri = uri;
    this.ideRelativePath = relativePath;
    this.configScopeId = configScopeId;
    this.isTest = isTest;
    this.charset = charset;
    this.fsPath = fsPath;
    this.content = content;
  }

  public URI getUri() {
    return uri;
  }

  public Path getIdeRelativePath() {
    return ideRelativePath;
  }

  public String getConfigScopeId() {
    return configScopeId;
  }

  @Nullable
  public Boolean isTest() {
    return isTest;
  }

  public String getCharset() {
    return charset;
  }

  public Path getFsPath() {
    return fsPath;
  }

  @Nullable
  public String getContent() {
    return content;
  }
}
