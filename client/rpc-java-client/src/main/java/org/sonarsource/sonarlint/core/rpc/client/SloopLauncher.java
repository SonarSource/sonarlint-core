/*
 * SonarLint Core - RPC Java Client
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
package org.sonarsource.sonarlint.core.rpc.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

public class SloopLauncher {
  public static final String SLOOP_CLI_ENTRYPOINT_CLASS = "org.sonarsource.sonarlint.core.backend.cli.SonarLintServerCli";
  private final SonarLintRpcClientDelegate rpcClient;
  private final Function<List<String>, ProcessBuilder> processBuilderFactory;
  private final Supplier<String> osNameSupplier;

  public SloopLauncher(SonarLintRpcClientDelegate rpcClient) {
    this(rpcClient, ProcessBuilder::new, () -> System.getProperty("os.name"));
  }

  SloopLauncher(SonarLintRpcClientDelegate rpcClient, Function<List<String>, ProcessBuilder> processBuilderFactory, Supplier<String> osNameSupplier) {
    this.rpcClient = rpcClient;
    this.processBuilderFactory = processBuilderFactory;
    this.osNameSupplier = osNameSupplier;
  }

  public Sloop start(Path distPath) {
    return start(distPath, null);
  }

  public Sloop start(Path distPath, @Nullable Path jrePath) {
    return start(distPath, jrePath, null);
  }

  /**
   * @param jvmOpts Each argument should be separated by a space, such as '-XX:+UseG1GC -XX:MaxHeapFreeRatio=50'
   */
  public Sloop start(Path distPath, @Nullable Path jrePath, @Nullable String jvmOpts) {
    try {
      return execute(distPath, jrePath, jvmOpts);
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

  private Sloop execute(Path distPath, @Nullable Path jrePath, @Nullable String jvmOpts) throws IOException {
    var jreHomePath = jrePath == null ? distPath.resolve("jre") : jrePath;
    logToClient(LogLevel.INFO, "Using JRE from " + jreHomePath, null);
    var binDirPath = jreHomePath.resolve("bin");
    var jreJavaExePath = binDirPath.resolve("java" + (isWindows() ? ".exe" : ""));
    if (!Files.exists(jreJavaExePath)) {
      throw new IllegalArgumentException("The provided JRE path does not exist: " + jreJavaExePath);
    }
    var processBuilder = processBuilderFactory.apply(createCommand(distPath, jreJavaExePath, jvmOpts));
    processBuilder.directory(binDirPath.toFile());
    processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
    processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
    processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

    var process = processBuilder.start();

    // redirect process.getErrorStream() to the client logs
    new StreamGobbler(process.getErrorStream(), stdErrLogConsumer()).start();
    // use process.getInputStream() as an input for the client
    var serverToClientInputStream = process.getInputStream();
    // use process.getOutputStream() as the standard input of a subprocess that can be written to
    var clientToServerOutputStream = process.getOutputStream();
    var clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, rpcClient);
    process.onExit().thenAccept(p -> clientLauncher.close());

    var serverProxy = clientLauncher.getServerProxy();
    return new Sloop(serverProxy, process);
  }

  private static List<String> createCommand(Path distPath, Path jreJavaExePath, @Nullable String clientJvmOpts) {
    var libFolderPath = distPath.resolve("lib");
    var classpath = libFolderPath.toAbsolutePath().normalize() + File.separator + '*';
    List<String> commands = new ArrayList<>();
    commands.add(jreJavaExePath.toAbsolutePath().normalize().toString());
    var sonarlintEnvJvmOpts = System.getenv("SONARLINT_JVM_OPTS");
    if (sonarlintEnvJvmOpts != null) {
      commands.add(sonarlintEnvJvmOpts);
    }
    if (clientJvmOpts != null) {
      commands.addAll(Arrays.asList(clientJvmOpts.split(" ")));
    }
    // Avoid displaying the Java icon in the taskbar on Mac
    commands.add("-Djava.awt.headless=true");
    commands.add("-classpath");
    commands.add(classpath);
    commands.add(SLOOP_CLI_ENTRYPOINT_CLASS);
    return commands;
  }

  private Consumer<String> stdErrLogConsumer() {
    return s -> logToClient(LogLevel.ERROR, "StdErr: " + s, null);
  }

  private void logToClient(LogLevel level, @Nullable String message, @Nullable String stacktrace) {
    rpcClient.log(new LogParams(level, message, null, Thread.currentThread().getName(),
      SloopLauncher.class.getName(), stacktrace, Instant.now()));
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
