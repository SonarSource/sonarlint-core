/*
 * SonarLint Daemon
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
package org.sonarlint.daemon.services;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration.Builder;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.AnalysisReq;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.InputFile;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue.Severity;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.LogEvent;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.RuleDetails;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.RuleKey;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.StandaloneConfiguration;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Void;
import org.sonarsource.sonarlint.daemon.proto.StandaloneSonarLintGrpc;

public class StandaloneSonarLintImpl extends StandaloneSonarLintGrpc.StandaloneSonarLintImplBase {
  private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneSonarLintImpl.class);
  private StandaloneSonarLintEngine engine;
  private ProxyLogOutput logOutput = new ProxyLogOutput();

  static class DefaultClientInputFile implements ClientInputFile {
    private final Path path;
    private final boolean isTest;
    private final Charset charset;
    private final String userObject;

    protected DefaultClientInputFile(Path path, boolean isTest, Charset charset, String userObject) {
      this.path = path;
      this.isTest = isTest;
      this.charset = charset;
      this.userObject = userObject;
    }

    @Override
    public Path getPath() {
      return path;
    }

    @Override
    public boolean isTest() {
      return isTest;
    }

    @Override
    public Charset getCharset() {
      return charset;
    }

    @Override
    public <G> G getClientObject() {
      return (G) userObject;
    }
  }

  static class ProxyIssueListener implements IssueListener {
    private final StreamObserver<Issue> observer;

    protected ProxyIssueListener(StreamObserver<Issue> observer) {
      this.observer = observer;
    }

    @Override
    public void handle(org.sonarsource.sonarlint.core.client.api.common.analysis.Issue issue) {
      Severity severity;
      ClientInputFile inputFile = issue.getInputFile();

      switch (issue.getSeverity()) {
        case "MINOR":
          severity = Severity.MINOR;
          break;
        case "BLOCKER":
          severity = Severity.BLOCKER;
          break;
        case "INFO":
          severity = Severity.INFO;
          break;
        case "CRITICAL":
          severity = Severity.CRITICAL;
          break;
        case "MAJOR":
        default:
          severity = Severity.MAJOR;
          break;
      }

      Issue.Builder builder = Issue.newBuilder();
      builder.setRuleKey(issue.getRuleKey())
        .setRuleName(issue.getRuleName())
        .setMessage(issue.getMessage())
        .setSeverity(severity)
        .setStartLine(issue.getStartLine() != null ? issue.getStartLine() : 0)
        .setStartLineOffset(issue.getStartLineOffset() != null ? issue.getStartLineOffset() : 0)
        .setEndLine(issue.getEndLine() != null ? issue.getEndLine() : 0)
        .setEndLineOffset(issue.getEndLineOffset() != null ? issue.getEndLineOffset() : 0);

      if (inputFile != null) {
        builder.setFilePath(inputFile.getPath().toString())
          .setUserObject((String) inputFile.getClientObject());
      }

      observer.onNext(builder.build());
    }
  }

  static class ProxyLogOutput implements LogOutput {
    private StreamObserver<LogEvent> response;

    protected void setObserver(StreamObserver<LogEvent> response) {
      if (this.response != null) {
        this.response.onCompleted();
      }
      this.response = response;
    }

    @Override
    public synchronized void log(String formattedMessage, Level level) {
      if (level == Level.ERROR) {
        System.err.println(formattedMessage);
      } else {
        System.out.println(formattedMessage);
      }

      if (response != null) {
        LogEvent log = LogEvent.newBuilder()
          .setLevel(level.name())
          .setLog(formattedMessage)
          .setIsDebug(level == Level.DEBUG || level == Level.TRACE)
          .build();
        try {
          response.onNext(log);
        } catch (StatusRuntimeException e) {
          System.out.println("Log stream closed: " + e.getMessage());
          response = null;
        }
      }
    }
  }

  @Override
  public void start(StandaloneConfiguration requestConfig, StreamObserver<Void> response) {
    if (engine != null) {
      engine.stop();
      engine = null;
    }

    try {
      Builder builder = StandaloneGlobalConfiguration.builder();

      for (String pluginPath : requestConfig.getPluginUrlList()) {
        builder.addPlugin(new URL(pluginPath));
      }

      if (requestConfig.getHomePath() != null) {
        builder.setSonarLintUserHome(Paths.get(requestConfig.getHomePath()));
      }

      engine = new StandaloneSonarLintEngineImpl(builder.build());
      response.onNext(Void.newBuilder().build());
      response.onCompleted();
    } catch (Exception e) {
      LOGGER.error("Error registering", e);
      response.onError(e);
    }
  }

  @Override
  public void analyze(AnalysisReq requestConfig, StreamObserver<Issue> response) {
    if (engine == null) {
      response.onError(new IllegalStateException("Not registered"));
      return;
    }

    try {
      List<ClientInputFile> files = new LinkedList<>();
      List<InputFile> requestFiles = requestConfig.getFileList();

      for (InputFile f : requestFiles) {
        files.add(new DefaultClientInputFile(Paths.get(f.getPath()), f.getIsTest(), Charset.forName(f.getCharset()), f.getUserObject()));
      }

      StandaloneAnalysisConfiguration config = new StandaloneAnalysisConfiguration(
        Paths.get(requestConfig.getBaseDir()),
        Paths.get(requestConfig.getWorkDir()),
        files,
        requestConfig.getPropertiesMap());

      engine.analyze(config, new ProxyIssueListener(response), logOutput);
      response.onCompleted();
    } catch (Exception e) {
      LOGGER.error("Error analyzing", e);
      response.onError(e);
    }
  }

  @Override
  public void streamLogs(Void request, StreamObserver<LogEvent> response) {
    logOutput.setObserver(response);
  }

  @Override
  public void getRuleDetails(RuleKey key, StreamObserver<RuleDetails> response) {
    try {
      org.sonarsource.sonarlint.core.client.api.common.RuleDetails ruleDetails = engine.getRuleDetails(key.getKey());
      response.onNext(RuleDetails.newBuilder()
        .setKey(ruleDetails.getKey())
        .setName(ruleDetails.getName())
        .setLanguage(ruleDetails.getLanguage())
        .setSeverity(ruleDetails.getSeverity())
        .setHtmlDescription(ruleDetails.getHtmlDescription())
        .addAllTags(Arrays.asList(ruleDetails.getTags()))
        .build());
      response.onCompleted();
    } catch (Exception e) {
      LOGGER.error("getRuleDetails", e);
      response.onError(e);
    }
  }

}
