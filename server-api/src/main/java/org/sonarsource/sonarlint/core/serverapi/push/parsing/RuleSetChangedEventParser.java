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
package org.sonarsource.sonarlint.core.serverapi.push.parsing;

import com.google.gson.Gson;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.push.RuleSetChangedEvent;

import static org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils.areBlank;
import static org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils.isBlank;

public class RuleSetChangedEventParser implements EventParser<RuleSetChangedEvent> {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Gson gson = new Gson();

  @Override
  public Optional<RuleSetChangedEvent> parse(String jsonData) {
    var payload = gson.fromJson(jsonData, RuleSetChangedEventPayload.class);
    if (payload.isInvalid()) {
      LOG.error("Invalid payload for 'RuleSetChanged' event: {}", jsonData);
      return Optional.empty();
    }
    return Optional.of(new RuleSetChangedEvent(
      payload.projects,
      payload.activatedRules.stream().map(changedRule -> new RuleSetChangedEvent.ActiveRule(
        changedRule.key,
        changedRule.language,
        IssueSeverity.valueOf(changedRule.severity),
        changedRule.params.stream().collect(Collectors.toMap(p -> p.key, p -> p.value)),
        changedRule.templateKey))
        .collect(Collectors.toList()),
      payload.deactivatedRules));
  }

  private static class RuleSetChangedEventPayload {
    private List<String> projects;
    private List<ActiveRulePayload> activatedRules;
    private List<String> deactivatedRules;

    private boolean isInvalid() {
      return isBlank(projects) || areBlank(activatedRules, deactivatedRules);
    }

    private static class ActiveRulePayload {
      private String key;
      private String language;
      private String severity;
      private List<RuleParameterPayload> params;
      private String templateKey;
    }

    private static class RuleParameterPayload {
      private String key;
      private String value;
    }
  }
}
