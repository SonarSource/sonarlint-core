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
package org.sonarsource.sonarlint.core.serverapi.fixsuggestions;

import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.exception.TooManyRequestsException;

public class FixSuggestionsApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerApiHelper helper;

  public FixSuggestionsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public AiSuggestionResponseBodyDto getAiSuggestion(AiSuggestionRequestBodyDto dto, SonarLintCancelMonitor cancelMonitor) {
    try {
      return helper.isSonarCloud()
        ? helper.apiPostJson("/fix-suggestions/ai-suggestions", dto, AiSuggestionResponseBodyDto.class, cancelMonitor)
        : helper.postJson("/api/v2/fix-suggestions/ai-suggestions", dto, AiSuggestionResponseBodyDto.class, cancelMonitor);
    } catch (TooManyRequestsException e) {
      throw e;
    } catch (Exception e) {
      LOG.error("Error while generating an AI CodeFix", e);
      throw e;
    }
  }

  public SupportedRulesResponseDto getSupportedRules(SonarLintCancelMonitor cancelMonitor) {
    try {
      return helper.isSonarCloud()
        ? helper.apiGetJson("/fix-suggestions/supported-rules", SupportedRulesResponseDto.class, cancelMonitor)
        : helper.getJson("/api/v2/fix-suggestions/supported-rules", SupportedRulesResponseDto.class, cancelMonitor);
    } catch (Exception e) {
      LOG.error("Error while fetching the list of AI CodeFix supported rules", e);
      throw e;
    }
  }

  public OrganizationConfigsResponseDto getOrganizationConfigs(String organizationId, SonarLintCancelMonitor cancelMonitor) {
    try {
      return helper.apiGetJson("/fix-suggestions/organization-configs/" + UrlUtils.urlEncode(organizationId),
        OrganizationConfigsResponseDto.class, cancelMonitor);
    } catch (Exception e) {
      LOG.error("Error while fetching the AI CodeFix organization config", e);
      throw e;
    }
  }
}
