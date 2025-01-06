/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;

public class BackendJsonRpcLauncher implements Closeable {

  private final SonarLintRpcServerImpl server;

  public BackendJsonRpcLauncher(InputStream in, OutputStream out) {
    server = new SonarLintRpcServerImpl(in, out);
  }

  public SonarLintRpcServerImpl getServer() {
    return server;
  }

  /**
   * @deprecated All related codes moved to org.sonarsource.sonarlint.core.rpc.impl.SonarLintRpcServerImpl#shutdown()
   * Calling server shutdown method is enough.
   */
  @Override
  @Deprecated(since = "10.4", forRemoval = true)
  public void close() {
    // This method is used by the language server. It will be removed once the usage has been removed
  }
}
