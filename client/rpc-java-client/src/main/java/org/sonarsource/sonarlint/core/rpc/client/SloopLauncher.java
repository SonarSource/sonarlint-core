/*
 * SonarLint Core - RPC Java Client
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
package org.sonarsource.sonarlint.core.rpc.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

public class SloopLauncher {

  private static final String WIN_LAUNCHER_SCRIPT = "sonarlint-backend.bat";
  private static final String UNIX_LAUNCHER_SCRIPT = "sonarlint-backend";
  private final SonarLintRpcClientDelegate rpcClient;
  private final Function<List<String>, ProcessBuilder> processBuilderFactory;
  private final Supplier<String> osNameSupplier;
  private Process process;
  private SonarLintRpcServer serverProxy;

  public SloopLauncher(SonarLintRpcClientDelegate rpcClient) {
    this(rpcClient, ProcessBuilder::new, () -> System.getProperty("os.name"));
  }

  SloopLauncher(SonarLintRpcClientDelegate rpcClient, Function<List<String>, ProcessBuilder> processBuilderFactory, Supplier<String> osNameSupplier) {
    this.rpcClient = rpcClient;
    this.processBuilderFactory = processBuilderFactory;
    this.osNameSupplier = osNameSupplier;
  }

  public void start(Path distPath) {
    start(distPath, null);
  }


  public void start(Path distPath, @Nullable Path jrePath) {
    try {
      execute(distPath, jrePath);
    } catch (Exception e) {
      logToClient(LogLevel.ERROR, "Unable to start the SonarLint backend", stackTraceToString(e));
      throw new IllegalStateException("Unable to start the SonarLint backend", e);
    }
  }

  private static String stackTraceToString(Throwable t) {
    var stringWriter = new StringWriter();
    var printWriter = new PrintWriter(stringWriter);
    t.printStackTrace(printWriter);
    return stringWriter.toString();
  }

  /**
   * Inspired from Apache commons-lang3
   */
  private boolean isWindows() {
    var osName = osNameSupplier.get();
    if (osName == null) {
      return false;
    }
    return osName.startsWith("Windows");
  }

  private void execute(Path distPath, @Nullable Path jrePath) throws IOException {
    var binDirPath = distPath.resolve("bin");
    List<String> commands = new ArrayList<>();
    if (isWindows()) {
      commands.add("cmd.exe");
      commands.add("/c");
      commands.add(WIN_LAUNCHER_SCRIPT);
    } else {
      commands.add("sh");
      commands.add(UNIX_LAUNCHER_SCRIPT);
    }
    if (jrePath != null) {
      logToClient(LogLevel.INFO, "Using JRE from " + jrePath, null);
      commands.add("-j");
      commands.add(jrePath.toString());
    }

    var processBuilder = processBuilderFactory.apply(commands);
    processBuilder.directory(binDirPath.toFile());
    processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
    processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
    processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

    this.process = processBuilder.start();

    // redirect process.getErrorStream() to the client logs
    new StreamGobbler(process.getErrorStream(), stdErrLogConsumer()).start();
    // use process.getInputStream() as an input for the client
    var serverToClientInputStream = process.getInputStream();
    // use process.getOutputStream() as the standard input of a subprocess that can be written to
    var clientToServerOutputStream = process.getOutputStream();
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, rpcClient);

    serverProxy = clientLauncher.getServerProxy();
  }

  private Consumer<String> stdErrLogConsumer() {
    return s -> logToClient(LogLevel.ERROR, "StdErr: " + s, null);
  }

  private void logToClient(LogLevel level, @Nullable String message, @Nullable String stacktrace) {
    rpcClient.log(new LogParams(level, message, null, Thread.currentThread().getName(),
      SloopLauncher.class.getName(), stacktrace, Instant.now()));
  }

  public SonarLintRpcServer getServerProxy() {
    return serverProxy;
  }

  public int waitFor() throws InterruptedException {
    if (process.waitFor(1, TimeUnit.MINUTES)) {
      return process.exitValue();
    } else {
      logToClient(LogLevel.ERROR, "Unable to stop the SonarLint process in a timely manner. Killing it.", null);
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
