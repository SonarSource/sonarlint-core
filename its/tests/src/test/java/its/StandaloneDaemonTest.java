/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2009-2019 SonarSource SA
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
package its;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientCall.Listener;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import its.tools.SonarlintDaemon;
import its.tools.SonarlintProject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.AnalysisReq;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.AnalysisReq.Builder;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.InputFile;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.LogEvent;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Void;
import org.sonarsource.sonarlint.daemon.proto.StandaloneSonarLintGrpc;
import org.sonarsource.sonarlint.daemon.proto.StandaloneSonarLintGrpc.StandaloneSonarLintBlockingStub;

import static org.assertj.core.api.Assertions.assertThat;

public class StandaloneDaemonTest {
  @Rule
  public SonarlintDaemon daemon = new SonarlintDaemon();

  @Rule
  public SonarlintProject clientTools = new SonarlintProject();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private ManagedChannel channel;

  @Before
  public void setUp() {
    daemon.install();
    daemon.run();
    daemon.waitReady();
    channel = ManagedChannelBuilder.forAddress("localhost", 8050)
      .usePlaintext(true)
      .build();
  }

  @After
  public void shutdown() throws Exception {
    ClientCall<Void, Void> call = channel.newCall(StandaloneSonarLintGrpc.getShutdownMethod(), CallOptions.DEFAULT);
    call.start(new Listener<Void>() {
    }, new Metadata());
    call.sendMessage(Void.newBuilder().build());
    call.halfClose();
    call.request(1);

    channel.shutdownNow();
    channel.awaitTermination(2, TimeUnit.SECONDS);
  }

  @Test
  public void testJS() throws InterruptedException, IOException {

    LogCollector logs = new LogCollector();
    StandaloneSonarLintBlockingStub sonarlint = StandaloneSonarLintGrpc.newBlockingStub(channel);

    AnalysisReq analysisConfig = createAnalysisConfig("sample-javascript");

    long start = System.currentTimeMillis();

    ClientCall<Void, LogEvent> call = getLogs(logs, channel);
    try {
      for (int i = 0; i < 10; i++) {
        System.out.println("ITERATION: " + i);
        Iterator<Issue> issues = sonarlint.analyze(analysisConfig);

        assertThat(issues).hasSize(1);
        // Give some time for logs to come
        Thread.sleep(500);
        List<String> logsLines = logs.getLogsAndClear();
        // To be sure logs are not flooded by low level logs
        assertThat(logsLines.size()).isLessThan(100);
        assertThat(logsLines).contains("1 file indexed");
      }
    } finally {
      call.cancel("no more logs needed", null);
    }

    System.out.println("TIME " + (System.currentTimeMillis() - start));
  }

  @Test
  public void testC() throws InterruptedException, IOException {

    LogCollector logs = new LogCollector();
    StandaloneSonarLintBlockingStub sonarlint = StandaloneSonarLintGrpc.newBlockingStub(channel);

    AnalysisReq analysisConfig = createAnalysisConfig("sample-c");
    FileUtils.write(
      new File(analysisConfig.getBaseDir(), "build-wrapper-dump.json"),
      "{\"version\":0,\"captures\":[" +
        "{" +
        "\"compiler\": \"clang\"," +
        "\"executable\": \"compiler\"," +
        "\"stdout\": \"#define __STDC_VERSION__ 201112L\n\"," +
        "\"stderr\": \"\"" +
        "}," +
        "{" +
        "\"compiler\": \"clang\"," +
        "\"executable\": \"compiler\"," +
        "\"stdout\": \"#define __cplusplus 201703L\n\"," +
        "\"stderr\": \"\"" +
        "}," +
        "{\"compiler\":\"clang\",\"cwd\":\"" +
        analysisConfig.getBaseDir().replace("\\", "\\\\") +
        "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"src/file.c\"]}]}",
      StandardCharsets.UTF_8);

    analysisConfig = AnalysisReq.newBuilder(analysisConfig)
      .putProperties("sonar.cfamily.build-wrapper-output", Paths.get(analysisConfig.getBaseDir()).toAbsolutePath().toString())
      .build();

    long start = System.currentTimeMillis();

    ClientCall<Void, LogEvent> call = getLogs(logs, channel);
    try {
      Iterator<Issue> issues = sonarlint.analyze(analysisConfig);

      assertThat(issues).hasSize(1);
      // Give some time for logs to come
      Thread.sleep(500);
      List<String> logsLines = logs.getLogsAndClear();
      // To be sure logs are not flooded by low level logs
      assertThat(logsLines.size()).isLessThan(100);
      assertThat(logsLines).contains("1 file indexed");
    } finally {
      call.cancel("no more logs needed", null);
    }

    System.out.println("TIME " + (System.currentTimeMillis() - start));
  }

  private ClientCall<Void, LogEvent> getLogs(LogCollector collector, Channel channel) {
    ClientCall<Void, LogEvent> call = channel.newCall(StandaloneSonarLintGrpc.getStreamLogsMethod(), CallOptions.DEFAULT);
    call.start(collector, new Metadata());
    call.sendMessage(Void.newBuilder().build());
    call.halfClose();
    call.request(Integer.MAX_VALUE);
    return call;
  }

  private AnalysisReq createAnalysisConfig(String projectName) throws IOException {
    Path projectPath = clientTools.deployProject(projectName);
    List<Path> sourceFiles = clientTools.collectAllFiles(projectPath.resolve("src"));
    Builder builder = AnalysisReq.newBuilder();

    for (Path p : sourceFiles) {
      InputFile file = InputFile.newBuilder()
        .setCharset(StandardCharsets.UTF_8.name())
        .setPath(p.toAbsolutePath().toString())
        .setIsTest(false)
        .build();
      builder.addFile(file);
    }
    return builder
      .setBaseDir(projectPath.toAbsolutePath().toString())
      .setWorkDir(temp.newFolder().getAbsolutePath())
      .putAllProperties(Collections.singletonMap("key", "value"))
      .build();
  }

  private static class LogCollector extends ClientCall.Listener<LogEvent> {
    private List<LogEvent> list = Collections.synchronizedList(new LinkedList<LogEvent>());

    @Override
    public void onMessage(LogEvent log) {
      list.add(log);
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
      System.out.println("LOGS CLOSED " + status);
    }

    public List<String> getLogsAndClear() {
      synchronized (list) {
        List<String> result = list.stream().map(LogEvent::getLog).collect(Collectors.toList());
        list.clear();
        return result;
      }
    }

    public List<LogEvent> get() {
      return list;
    }
  }
}
