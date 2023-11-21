/*
 * SonarLint Core - RPC Java Client
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
<<<<<<<< HEAD:rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/client/analysis/DidChangeNodeJsParams.java
package org.sonarsource.sonarlint.core.rpc.protocol.client.analysis;

import java.nio.file.Path;
import javax.annotation.Nullable;

public class DidChangeNodeJsParams {

  private final Path nodeJsPath;

  private final String version;

  public DidChangeNodeJsParams(@Nullable Path nodeJsPath, @Nullable String version) {
    this.nodeJsPath = nodeJsPath;
    this.version = version;
  }

  public Path getNodeJsPath() {
    return nodeJsPath;
  }

  public String getVersion() {
    return version;
  }
========
package org.sonarsource.sonarlint.core.rpc.client;

public class ConnectionNotFoundException extends Exception {
>>>>>>>> f63286f2b (SLCORE-626 Remove unnecessary RPC wrapping on client side (#783)):rpc-java-client/src/main/java/org/sonarsource/sonarlint/core/rpc/client/ConnectionNotFoundException.java
}
