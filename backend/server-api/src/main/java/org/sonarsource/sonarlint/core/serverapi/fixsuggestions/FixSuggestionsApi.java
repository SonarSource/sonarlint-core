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
package org.sonarsource.sonarlint.core.serverapi.fixsuggestions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.exception.TooManyRequestsException;
import org.sonarsource.sonarlint.core.serverapi.exception.UnexpectedBodyException;

import static org.sonarsource.sonarlint.core.http.HttpClient.JSON_CONTENT_TYPE;

public class FixSuggestionsApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerApiHelper helper;

  public FixSuggestionsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public AiSuggestionResponseBodyDto getAiSuggestion(AiSuggestionRequestBodyDto dto, SonarLintCancelMonitor cancelMonitor) {
    // avoid Gson replacing characters like < > or = with Unicode representation
    var gson = new GsonBuilder().disableHtmlEscaping().create();
    try (var response = helper.isSonarCloud() ? helper.apiPost("/fix-suggestions/ai-suggestions", JSON_CONTENT_TYPE, gson.toJson(dto), cancelMonitor)
      : helper.post("/api/v2/fix-suggestions/ai-suggestions", JSON_CONTENT_TYPE, gson.toJson(dto), cancelMonitor)) {
      return gson.fromJson(response.bodyAsString(), AiSuggestionResponseBodyDto.class);
    } catch (TooManyRequestsException e) {
      throw e;
    } catch (Exception e) {
      LOG.error("Error while generating an AI CodeFix", e);
      throw new UnexpectedBodyException(e);
    }
  }

  public SupportedRulesResponseDto getSupportedRules(SonarLintCancelMonitor cancelMonitor) {
    try (
      var response = helper.isSonarCloud() ? helper.apiGet("/fix-suggestions/supported-rules", cancelMonitor)
        : helper.get("/api/v2/fix-suggestions/supported-rules", cancelMonitor)) {
      return new Gson().fromJson(response.bodyAsString(), SupportedRulesResponseDto.class);
    } catch (Exception e) {
      LOG.error("Error while fetching the list of AI CodeFix supported rules", e);
      throw new UnexpectedBodyException(e);
    }
  }

  public OrganizationConfigsResponseDto getOrganizationConfigs(String organizationId, SonarLintCancelMonitor cancelMonitor) {
    try (var response = helper.apiGet("/fix-suggestions/organization-configs/" + UrlUtils.urlEncode(organizationId), cancelMonitor)) {
      return new Gson().fromJson(response.bodyAsString(), OrganizationConfigsResponseDto.class);
    } catch (Exception e) {
      LOG.error("Error while fetching the AI CodeFix organization config", e);
      throw new UnexpectedBodyException(e);
    }
  }
}
