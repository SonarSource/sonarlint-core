/*
 * SonarLint Daemon
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarlint.daemon.model;

import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.Issue.Severity;

import io.grpc.stub.StreamObserver;

public class ProxyIssueListener implements IssueListener {
  private final StreamObserver<Issue> observer;

  public ProxyIssueListener(StreamObserver<Issue> observer) {
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
      builder.setFilePath(inputFile.getPath());
      if (inputFile.getClientObject() != null) {
        builder.setUserObject((String) inputFile.getClientObject());
      }
    }

    observer.onNext(builder.build());
  }
}
