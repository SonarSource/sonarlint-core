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
import java.nio.charset.Charset;
import java.nio.file.Path;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue.Severity;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.LogEvent;

public abstract class AbstractSonarLint {
  protected AbstractSonarLint() {
    // nothing to do
  }

  protected static class DefaultClientInputFile implements ClientInputFile {
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

  protected static class ProxyIssueListener implements IssueListener {
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

  protected static class ProxyLogOutput implements LogOutput {
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
}
