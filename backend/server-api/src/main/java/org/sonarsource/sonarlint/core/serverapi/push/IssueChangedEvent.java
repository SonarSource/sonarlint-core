/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverapi.push;

import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;

public class IssueChangedEvent implements SonarProjectEvent {
  private final String projectKey;
  private final List<Issue> impactedIssues;
  private final IssueSeverity userSeverity;
  private final RuleType userType;
  private final Boolean resolved;

  public IssueChangedEvent(String projectKey, List<Issue> impactedIssues, @Nullable IssueSeverity userSeverity, @Nullable RuleType userType, @Nullable Boolean resolved) {
    this.projectKey = projectKey;
    this.impactedIssues = impactedIssues;
    this.userSeverity = userSeverity;
    this.userType = userType;
    this.resolved = resolved;
  }

  @Override
  public String getProjectKey() {
    return projectKey;
  }

  public List<Issue> getImpactedIssues() {
    return impactedIssues;
  }

  /**
   * @return null when not changed
   */
  @CheckForNull
  public IssueSeverity getUserSeverity() {
    return userSeverity;
  }

  /**
   * @return null when not changed
   */
  @CheckForNull
  public RuleType getUserType() {
    return userType;
  }

  /**
   * @return null when not changed
   */
  @CheckForNull
  public Boolean getResolved() {
    return resolved;
  }

  public static class Issue {
    private final String issueKey;
    private final String branchName;
    private final Map<SoftwareQuality, ImpactSeverity> impacts;

    public Issue(String issueKey, String branchName, Map<SoftwareQuality, ImpactSeverity> impacts) {
      this.issueKey = issueKey;
      this.branchName = branchName;
      this.impacts = impacts;
    }

    public String getIssueKey() {
      return issueKey;
    }

    public String getBranchName() {
      return branchName;
    }

    public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
      return impacts;
    }
  }
}
