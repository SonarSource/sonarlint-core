/*
 * SonarLint Core - ITs
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import its.tools.PluginLocator;
import its.tools.SonarlintDaemon;
import its.tools.SonarlintProject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.AnalysisReq;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.AnalysisReq.Builder;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.InputFile;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.LogEvent;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.StandaloneConfiguration;
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

  @Before
  public void setUp() {
    daemon.install();
    daemon.run();
    daemon.waitReady();
  }

  @Test
  public void test() throws InterruptedException, IOException {
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8050)
      .usePlaintext(true)
      .build();

    LogCollector logs = new LogCollector();
    StandaloneSonarLintBlockingStub sonarlint = StandaloneSonarLintGrpc.newBlockingStub(channel);
    sonarlint.start(createStandaloneConfig());

    AnalysisReq analysisConfig = createAnalysisConfig("sample-java");

    long start = System.currentTimeMillis();

    ClientCall<Void, LogEvent> call = getLogs(logs, channel);

    for (int i = 0; i < 100; i++) {
      System.out.println("ITERATION: " + i);
      Iterator<Issue> issues = sonarlint.analyze(analysisConfig);

      assertThat(issues).hasSize(2);
      assertThat(logs.getLogsAndClear()).contains("1 files indexed");
      call.cancel();
    }

    System.out.println("TIME " + (System.currentTimeMillis() - start));

    channel.shutdownNow();
    channel.awaitTermination(2, TimeUnit.SECONDS);
  }

  private ClientCall<Void, LogEvent> getLogs(LogCollector collector, Channel channel) {
    ClientCall<Void, LogEvent> call = channel.newCall(StandaloneSonarLintGrpc.METHOD_STREAM_LOGS, CallOptions.DEFAULT);
    call.start(collector, new Metadata());
    call.sendMessage(Void.newBuilder().build());
    call.halfClose();
    call.request(Integer.MAX_VALUE);
    return call;
  }

  private StandaloneConfiguration createStandaloneConfig() throws IOException {
    return StandaloneConfiguration.newBuilder()
      .addPluginUrl(PluginLocator.getJavaPluginUrl().toString())
      .addPluginUrl(PluginLocator.getPhpPluginUrl().toString())
      .addPluginUrl(PluginLocator.getJavaScriptPluginUrl().toString())
      .setHomePath(temp.newFolder().getAbsolutePath())
      .build();
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

    public String getLogsAndClear() {
      StringBuilder builder = new StringBuilder();
      synchronized (list) {
        for (LogEvent e : list) {
          if (e.getIsDebug()) {
            continue;
          }

          builder.append(e.getLog()).append("\n");
        }
        // list.clear();
      }

      return builder.toString();
    }

    public List<LogEvent> get() {
      return list;
    }
  }
}
