/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2023 SonarSource SA
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
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;

public class IssueChangedEvent implements ServerEvent {
  private final String projectKey;
  private final List<String> impactedIssueKeys;
  private final IssueSeverity userSeverity;
  private final RuleType userType;
  private final Boolean resolved;

  public IssueChangedEvent(String projectKey, List<String> impactedIssueKeys, @Nullable IssueSeverity userSeverity, @Nullable RuleType userType, @Nullable Boolean resolved) {
    this.projectKey = projectKey;
    this.impactedIssueKeys = impactedIssueKeys;
    this.userSeverity = userSeverity;
    this.userType = userType;
    this.resolved = resolved;
  }

  public String getProjectKey() {
    return projectKey;
  }

  public List<String> getImpactedIssueKeys() {
    return impactedIssueKeys;
  }

  @CheckForNull
  public IssueSeverity getUserSeverity() {
    return userSeverity;
  }

  @CheckForNull
  public RuleType getUserType() {
    return userType;
  }

  @CheckForNull
  public Boolean getResolved() {
    return resolved;
  }
}
