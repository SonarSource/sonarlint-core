/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.push.parsing;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.common.ImpactPayload;

import static org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils.isBlank;

public class IssueChangedEventParser implements EventParser<IssueChangedEvent> {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Gson gson = new Gson();

  @Override
  public Optional<IssueChangedEvent> parse(String jsonData) {
    var payload = gson.fromJson(jsonData, IssueChangedEventPayload.class);
    if (payload.isInvalid()) {
      LOG.error("Invalid payload for 'IssueChangedEvent' event: {}", jsonData);
      return Optional.empty();
    }
    return Optional.of(new IssueChangedEvent(
      payload.projectKey,
      payload.issues.stream()
        .map(issueChange ->
          new IssueChangedEvent.Issue(issueChange.issueKey, issueChange.branchName, adapt(issueChange.impacts))
        )
        .toList(),
      payload.userSeverity != null ? IssueSeverity.valueOf(payload.userSeverity) : null,
      payload.userType != null ? RuleType.valueOf(payload.userType) : null,
      payload.resolved));
  }

  public static Map<SoftwareQuality, ImpactSeverity> adapt(@Nullable ImpactPayload[] payloads) {
    if (payloads == null) {
      return Map.of();
    }
    return Arrays.stream(payloads)
      .collect(Collectors.toMap(
        payload -> SoftwareQuality.valueOf(payload.softwareQuality()),
        payload -> ImpactSeverity.valueOf(payload.severity())
      ));
  }

  private static class IssueChangedEventPayload {
    private String projectKey;
    private List<ChangedIssuePayload> issues;
    private String userSeverity;
    private String userType;
    private Boolean resolved;

    private boolean isInvalid() {
      return isBlank(projectKey) || isBlank(issues) || issues.stream().anyMatch(ChangedIssuePayload::isInvalid)
        || (isBlank(userSeverity) && isBlank(userType) && resolved == null);
    }

    private static class ChangedIssuePayload {
      private String issueKey;
      private String branchName;
      @Nullable
      private ImpactPayload[] impacts;

      private boolean isInvalid() {
        return isBlank(issueKey) || isBlank(branchName);
      }
    }
  }
}
