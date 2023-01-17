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
package org.sonarsource.sonarlint.core.serverapi.rules;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.exception.UnexpectedBodyException;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class RulesApi {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static final Map<Language, String> TAINT_REPOS_BY_LANGUAGE = Map.of(
    Language.CS, "roslyn.sonaranalyzer.security.cs",
    Language.JAVA, "javasecurity",
    Language.JS, "jssecurity",
    Language.TS, "tssecurity",
    Language.PHP, "phpsecurity",
    Language.PYTHON, "pythonsecurity");

  public static final Set<String> TAINT_REPOS = Set.copyOf(TAINT_REPOS_BY_LANGUAGE.values());

  public static final String RULE_SHOW_URL = "/api/rules/show.protobuf?key=";

  private final ServerApiHelper serverApiHelper;

  public RulesApi(ServerApiHelper serverApiHelper) {
    this.serverApiHelper = serverApiHelper;
  }

  public CompletableFuture<ServerRule> getRule(String ruleKey) {
    var builder = new StringBuilder(RULE_SHOW_URL + ruleKey);
    serverApiHelper.getOrganizationKey().ifPresent(org -> builder.append("&organization=").append(UrlUtils.urlEncode(org)));
    return serverApiHelper.getAsync(builder.toString())
      .thenApply(response -> {
        try (response) {
          var rule = Rules.ShowResponse.parseFrom(response.bodyAsStream()).getRule();
          return new ServerRule(rule.getName(), IssueSeverity.valueOf(rule.getSeverity()), RuleType.valueOf(rule.getType().name()), rule.getLang(), rule.getHtmlDesc(),
            convertDescriptionSections(rule),
            rule.getHtmlNote(), Set.copyOf(rule.getEducationPrinciples().getEducationPrinciplesList()));
        } catch (Exception e) {
          LOG.error("Error when fetching rule + '" + ruleKey + "'", e);
          throw new UnexpectedBodyException(e);
        }
      });
  }

  private static List<ServerRule.DescriptionSection> convertDescriptionSections(Rules.Rule rule) {
    if (rule.hasDescriptionSections()) {
      return rule.getDescriptionSections().getDescriptionSectionsList().stream()
        .map(s -> {
          ServerRule.DescriptionSection.Context context = null;
          if (s.hasContext()) {
            var contextFromServer = s.getContext();
            context = new ServerRule.DescriptionSection.Context(contextFromServer.getKey(), contextFromServer.getDisplayName());
          }
          return new ServerRule.DescriptionSection(s.getKey(), s.getContent(), Optional.ofNullable(context));
        }).collect(toList());
    }
    return Collections.emptyList();
  }

  public Collection<ServerActiveRule> getAllActiveRules(String qualityProfileKey, ProgressMonitor progress) {
    // Use a map to avoid duplicates during pagination
    Map<String, ServerActiveRule> activeRulesByKey = new HashMap<>();
    Map<String, String> ruleTemplatesByRuleKey = new HashMap<>();
    serverApiHelper.getPaginated(getSearchByQualityProfileUrl(qualityProfileKey),
      Rules.SearchResponse::parseFrom,
      Rules.SearchResponse::getTotal,
      r -> {
        ruleTemplatesByRuleKey.putAll(r.getRulesList().stream().collect(Collectors.toMap(Rules.Rule::getKey, Rules.Rule::getTemplateKey)));
        return List.copyOf(r.getActives().getActivesMap().entrySet());
      },
      activeEntry -> {
        var ruleKey = activeEntry.getKey();
        // Since we are querying rules for a given profile, we know there will be only one active rule per rule
        Rules.Active ar = activeEntry.getValue().getActiveListList().get(0);
        activeRulesByKey.put(ruleKey, new ServerActiveRule(
          ruleKey,
          IssueSeverity.valueOf(ar.getSeverity()),
          ar.getParamsList().stream().collect(Collectors.toMap(Rules.Active.Param::getKey, Rules.Active.Param::getValue)),
          ruleTemplatesByRuleKey.get(ruleKey)));

      },
      false,
      progress);
    return activeRulesByKey.values();
  }

  private String getSearchByQualityProfileUrl(String qualityProfileKey) {
    var builder = new StringBuilder();
    builder.append("/api/rules/search.protobuf?qprofile=");
    builder.append(UrlUtils.urlEncode(qualityProfileKey));
    serverApiHelper.getOrganizationKey().ifPresent(org -> builder.append("&organization=").append(UrlUtils.urlEncode(org)));
    builder.append("&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY,SECURITY_HOTSPOT&s=key");
    return builder.toString();
  }

  public Set<String> getAllTaintRules(List<Language> enabledLanguages, ProgressMonitor progress) {
    Set<String> taintRules = new HashSet<>();
    serverApiHelper.getPaginated(getSearchByRepoUrl(enabledLanguages.stream().map(TAINT_REPOS_BY_LANGUAGE::get).filter(Objects::nonNull).collect(toList())),
      Rules.SearchResponse::parseFrom,
      Rules.SearchResponse::getTotal,
      Rules.SearchResponse::getRulesList,
      rule -> taintRules.add(rule.getKey()),
      false,
      progress);
    return taintRules;
  }

  private String getSearchByRepoUrl(List<String> repositories) {
    var builder = new StringBuilder();
    builder.append("/api/rules/search.protobuf?repositories=");
    builder.append(repositories.stream().map(UrlUtils::urlEncode).collect(joining(",")));
    serverApiHelper.getOrganizationKey().ifPresent(org -> builder.append("&organization=").append(UrlUtils.urlEncode(org)));
    // Add only f=repo even if we don't need it, else too many fields are returned by default
    builder.append("&f=repo&s=key");
    return builder.toString();
  }

}
