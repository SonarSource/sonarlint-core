/*
 * SonarLint Server API
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.rules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.exception.UnexpectedBodyException;

public class RulesApi {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static final String RULE_SHOW_URL = "/api/rules/show.protobuf?key=";

  private final ServerApiHelper helper;

  public RulesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public CompletableFuture<ServerRule> getRule(String ruleKey) {
    var builder = new StringBuilder(RULE_SHOW_URL + ruleKey);
    helper.getOrganizationKey().ifPresent(org -> builder.append("&organization=").append(UrlUtils.urlEncode(org)));
    return helper.getAsync(builder.toString())
      .thenApply(response -> {
        try (response) {
          var rule = Rules.ShowResponse.parseFrom(response.bodyAsStream()).getRule();
          return new ServerRule(rule.getName(), rule.getHtmlDesc(), rule.getHtmlNote());
        } catch (Exception e) {
          LOG.error("Error when fetching rule + '" + ruleKey + "'", e);
          throw new UnexpectedBodyException(e);
        }
      });
  }

  public List<ServerActiveRule> getAllActiveRules(String qualityProfileKey, ProgressMonitor progress) {
    List<ServerActiveRule> activeRules = new ArrayList<>();
    var page = 0;
    var loaded = 0;

    while (true) {
      page++;
      var response = loadFromStream(helper.get(getSearchByQualityProfileUrl(qualityProfileKey, page)));
      var rules = response.getRulesList();
      for (var entry : response.getActives().getActivesMap().entrySet()) {
        var ruleKey = entry.getKey();
        for (Rules.Active ar : entry.getValue().getActiveListList()) {
          var rule = rules.stream().filter(r -> ruleKey.equals(r.getKey())).findFirst().orElseThrow();
          activeRules.add(new ServerActiveRule(
            entry.getKey(),
            ar.getSeverity(),
            ar.getParamsList().stream().collect(Collectors.toMap(Rules.Active.Param::getKey, Rules.Active.Param::getValue)),
            rule.getTemplateKey()));
        }
      }
      loaded += response.getPs();

      if (response.getTotal() <= loaded) {
        break;
      }
      progress.setProgressAndCheckCancel("Loading page " + page, loaded / (float) response.getTotal());
    }
    return activeRules;
  }

  private String getSearchByQualityProfileUrl(String qualityProfileKey, int page) {
    var builder = new StringBuilder();
    builder.append("/api/rules/search.protobuf?qprofile=");
    builder.append(qualityProfileKey);
    helper.getOrganizationKey().ifPresent(org -> builder.append("&organization=").append(UrlUtils.urlEncode(org)));
    builder.append("&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY&ps=500&p=");
    builder.append(page);
    return builder.toString();
  }

  private static Rules.SearchResponse loadFromStream(HttpClient.Response response) {
    try (var toBeClosed = response; var is = toBeClosed.bodyAsStream()) {
      return Rules.SearchResponse.parseFrom(is);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load rules", e);
    }
  }
}
