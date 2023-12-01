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
package org.sonarsource.sonarlint.core.rpc.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

public class SloopLauncher {

  private static final String WIN_LAUNCHER_SCRIPT = "sonarlint-backend.bat";
  private static final String UNIX_LAUNCHER_SCRIPT = "sonarlint-backend";
  private static SonarLintRpcClientDelegate client;
  private static Process process;

  private SloopLauncher() {
    // don't instantiate
  }

  @Nullable
  public static SonarLintRpcServer startSonarLintRpcServer(String distPath, SonarLintRpcClientDelegate clientArg) {
    return startAndGetSonarLintRpcServer(distPath, clientArg, "");
  }

  @Nullable
  public static SonarLintRpcServer startSonarLintRpcServerWithJre(String distPath, SonarLintRpcClientDelegate clientArg, String jrePath) {
    return startAndGetSonarLintRpcServer(distPath, clientArg, jrePath);
  }

  private static SonarLintRpcServer startAndGetSonarLintRpcServer(String distPath, SonarLintRpcClientDelegate clientArg, String jrePath) {
    try {
      SloopLauncher.client = clientArg;
      return execute(distPath, jrePath);
    } catch (Exception e) {
      client.log(new LogParams(LogLevel.ERROR, e.getMessage(), null));
    }
    return null;
  }

  /**
   * Inspired from Apache commons-lang3
   */
  private static boolean isWindows() {
    var osName = System.getProperty("os.name");
    if (osName == null) {
      return false;
    }
    return osName.startsWith("Windows");
  }

  private static SonarLintRpcServer execute(String distPath, String jrePath) {
    var binDirPath = distPath + File.separator + "bin";
    List<String> commands = new ArrayList<>();
    try {
      if (isWindows()) {
        commands.add("cmd.exe");
        commands.add("/c");
        commands.add(WIN_LAUNCHER_SCRIPT);
      } else {
        commands.add("sh");
        commands.add(UNIX_LAUNCHER_SCRIPT);
      }
      if (!jrePath.isEmpty()) {
        client.log(new LogParams(LogLevel.INFO, "Using JRE from " + jrePath, null));
        commands.add("-j");
        commands.add(jrePath);
      }

      var processBuilder = new ProcessBuilder(commands);
      processBuilder.directory(new File(binDirPath));
      processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
      processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
      processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

      process = processBuilder.start();

      // redirect process.getErrorStream() to the client logs
      new StreamGobbler(process.getErrorStream(), stdErrLogConsumer()).start();
      // use process.getInputStream() as an input for the client
      var serverToClientInputStream = process.getInputStream();
      // use process.getOutputStream() as the standard input of a subprocess that can be written to
      var clientToServerOutputStream = process.getOutputStream();
      var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, client);

      return clientLauncher.getServerProxy();
    } catch (Exception e) {
      client.log(new LogParams(LogLevel.WARN, e.getMessage(), ""));
    }
    return null;
  }

  private static Consumer<String> stdErrLogConsumer() {
    return s -> client.log(new LogParams(LogLevel.ERROR, "StdErr: " + s, null));
  }

  public static int waitFor() throws InterruptedException {
    if (process.waitFor(1, TimeUnit.MINUTES)) {
      return process.exitValue();
    } else {
      process.destroyForcibly();
      return -1;
    }
  }

  private static class StreamGobbler extends Thread {
    private final InputStream inputStream;
    private final Consumer<String> consumer;

    public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
      this.inputStream = inputStream;
      this.consumer = consumer;
    }

    @Override
    public void run() {
      new BufferedReader(new InputStreamReader(inputStream)).lines()
        .forEach(consumer);
    }
  }
}
