/*
 * SonarLint Language Server
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarlint.languageserver;

import java.util.List;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

public class DelegatingIssue implements Issue {
  private final Issue issue;

  DelegatingIssue(Issue issue) {
    this.issue = issue;
  }

  @Override
  public String getSeverity() {
    return issue.getSeverity();
  }

  @CheckForNull
  @Override
  public String getType() {
    return issue.getType();
  }

  @CheckForNull
  @Override
  public String getMessage() {
    return issue.getMessage();
  }

  @Override
  public String getRuleKey() {
    return issue.getRuleKey();
  }

  @Override
  public String getRuleName() {
    return issue.getRuleName();
  }

  @CheckForNull
  @Override
  public Integer getStartLine() {
    return issue.getStartLine();
  }

  @CheckForNull
  @Override
  public Integer getStartLineOffset() {
    return issue.getStartLineOffset();
  }

  @CheckForNull
  @Override
  public Integer getEndLine() {
    return issue.getEndLine();
  }

  @CheckForNull
  @Override
  public Integer getEndLineOffset() {
    return issue.getEndLineOffset();
  }

  @Override
  public List<Flow> flows() {
    return issue.flows();
  }

  @CheckForNull
  @Override
  public ClientInputFile getInputFile() {
    return issue.getInputFile();
  }

}
