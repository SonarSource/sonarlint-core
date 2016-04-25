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
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue.Severity;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.LogEvent;

import java.nio.charset.Charset;
import java.nio.file.Path;

public abstract class AbstractSonarLint {
  protected static class DefaultClientInputFile implements ClientInputFile {
    private final Path path;
    private final boolean isTest;
    private final Charset charset;

    protected DefaultClientInputFile(Path path, boolean isTest, Charset charset) {
      this.path = path;
      this.isTest = isTest;
      this.charset = charset;
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
      return null;
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

      observer.onNext(Issue.newBuilder()
        .setRuleKey(issue.getRuleKey())
        .setRuleName(issue.getRuleName())
        .setMessage(issue.getMessage())
        .setSeverity(severity)
        .setStartLine(issue.getStartLine())
        .setStartLineOffset(issue.getStartLineOffset())
        .setEndLine(issue.getEndLine())
        .setEndLineOffset(issue.getEndLineOffset())
        .build());
    }
  }

  protected static class ProxyLogOutput implements LogOutput {
    private StreamObserver<LogEvent> response;

    protected void setObserver(StreamObserver<LogEvent> response) {
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
          response = null;
        }
      }
    }
  }
}
