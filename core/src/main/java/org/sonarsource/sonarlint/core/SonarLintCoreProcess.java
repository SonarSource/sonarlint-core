/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import picocli.CommandLine;

public class SonarLintCoreProcess implements Callable<Integer> {

  @Override
  public Integer call() throws ExecutionException, InterruptedException {
    var server = new SonarLintBackendImpl();

    var launcher = new Launcher.Builder<SonarLintClient>()
      .setLocalService(server)
      .setRemoteInterface(SonarLintClient.class)
      .setInput(System.in)
      .setOutput(System.out)
      .create();

    server.setClient(launcher.getRemoteProxy());
    launcher.startListening().get();
    return 0;
  }

  public static void main(String... args) {
    var exitCode = new CommandLine(new SonarLintCoreProcess()).execute(args);
    System.exit(exitCode);
  }
}
