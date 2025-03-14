/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.remediation.aicodefix;

import org.sonarsource.sonarlint.core.repository.reporting.RaisedIssue;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.serverconnection.AiCodeFixSettings;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.tracking.TrackedIssue;

public record AiCodeFixFeature(AiCodeFixSettings settings) {
  public boolean isFixable(TrackedIssue issue) {
    return settings.supportedRules().contains(issue.getRuleKey()) && issue.getTextRangeWithHash() != null;
  }

  public boolean isFixable(RaisedIssue issue) {
    return settings.supportedRules().contains(issue.issueDto().getRuleKey()) && issue.issueDto().getTextRange() != null;
  }

  public boolean isFixable(ServerTaintIssue serverTaintIssue) {
    return settings.supportedRules().contains(serverTaintIssue.getRuleKey()) && serverTaintIssue.getTextRange() != null;
  }

  public boolean isFixable(TaintVulnerabilityDto taintDto) {
    return settings.supportedRules().contains(taintDto.getRuleKey()) && taintDto.getTextRange() != null;
  }
}
