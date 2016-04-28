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

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration.Builder;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.AnalysisReq;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.InputFile;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.LogEvent;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.RuleDetails;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.RuleKey;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.StandaloneConfiguration;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Void;
import org.sonarsource.sonarlint.daemon.proto.StandaloneSonarLintGrpc.StandaloneSonarLint;

import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class StandaloneSonarLintImpl extends AbstractSonarLint implements StandaloneSonarLint {
  private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneSonarLintImpl.class);
  private StandaloneSonarLintEngine engine;
  private ProxyLogOutput logOutput = new ProxyLogOutput();

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
        requestConfig.getProperties());

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
