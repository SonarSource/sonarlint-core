/*
 * SonarLint Server API
 * Copyright (C) 2016-2021 SonarSource SA
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.Progress;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class RulesApi {
  private static final Logger LOG = Loggers.get(RulesApi.class);

  public static final String RULES_SEARCH_URL = "/api/rules/search.protobuf?f=repo,name,severity,lang,htmlDesc,htmlNote,internalKey,isTemplate,templateKey,"
    + "actives&statuses=BETA,DEPRECATED,READY&types=CODE_SMELL,BUG,VULNERABILITY";
  public static final String RULE_SHOW_URL = "/api/rules/show.protobuf?key=";

  private final ServerApiHelper helper;

  public RulesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public Optional<String> getRuleDescription(String ruleKey) {
    try (var response = helper.get(RULE_SHOW_URL + ruleKey)) {
      var rule = Rules.ShowResponse.parseFrom(response.bodyAsStream()).getRule();
      return Optional.of(rule.getHtmlDesc() + "\n" + rule.getHtmlNote());
    } catch (Exception e) {
      LOG.error("Error when fetching rule", e);
      return Optional.empty();
    }
  }

  public ServerRules getAll(Set<String> enabledLanguageKeys, Progress progress) {
    Map<RuleKey, ServerRules.Rule> rulesByKey = new HashMap<>();
    Map<String, List<ServerRules.ActiveRule>> activeRulesByQProfileKey = new HashMap<>();

    for (var i = 0; i < Severity.values().length; i++) {
      var severity = Severity.values()[i];
      progress.setProgressAndCheckCancel("Loading severity '" + severity.name().toLowerCase(Locale.US) + "'",
        i / (float) Severity.values().length);
      Progress severityProgress = progress.subProgress(i / (float) Severity.values().length,
        (i + 1) / (float) Severity.values().length, severity.name().toLowerCase(Locale.US));
      fetchRulesAndActiveRules(rulesByKey, severity.name(), activeRulesByQProfileKey, enabledLanguageKeys, severityProgress);
    }
    return new ServerRules(new ArrayList<>(rulesByKey.values()), activeRulesByQProfileKey);
  }

  public List<ServerRules.ActiveRule> getAllActiveRules(String qualityProfileKey, Progress progress) {
    List<ServerRules.ActiveRule> activeRules = new ArrayList<>();
    var page = 0;
    var loaded = 0;

    while (true) {
      page++;
      Rules.SearchResponse response = loadFromStream(helper.get(getSearchByQualityProfileUrl(qualityProfileKey, page)));
      for (var entry : response.getActives().getActives().entrySet()) {
        var ruleKey = RuleKey.parse(entry.getKey());
        for (Rules.Active ar : entry.getValue().getActiveListList()) {
          activeRules.add(new ServerRules.ActiveRule(
            ruleKey,
            ar.getSeverity(),
            ar.getParamsList().stream().map(p -> new ServerRules.ActiveRule.Param(p.getKey(), p.getValue())).collect(Collectors.toList())));
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

  private void fetchRulesAndActiveRules(Map<RuleKey, ServerRules.Rule> rulesByKey, String severity, Map<String, List<ServerRules.ActiveRule>> activeRulesByQProfileKey,
    Set<String> enabledLanguageKeys, Progress progress) {
    var page = 0;
    var pageSize = 500;
    var loaded = 0;

    while (true) {
      page++;
      Rules.SearchResponse response = loadFromStream(helper.get(getSearchBySeverityUrl(severity, enabledLanguageKeys, page, pageSize)));
      if (response.getTotal() > 10_000) {
        throw new IllegalStateException(
          String.format("Found more than 10000 rules for severity '%s' in the SonarQube server, which is not supported by SonarLint.", severity));
      }
      readPage(rulesByKey, activeRulesByQProfileKey, response);
      loaded += response.getPs();

      if (response.getTotal() <= loaded) {
        break;
      }
      progress.setProgressAndCheckCancel("Loading page " + page, loaded / (float) response.getTotal());
    }
  }

  private String getSearchByQualityProfileUrl(String qualityProfileKey, int page) {
    var builder = new StringBuilder();
    builder.append("/api/rules/search.protobuf?qprofile=");
    builder.append(qualityProfileKey);
    helper.getOrganizationKey().ifPresent(org -> builder.append("&organization=").append(StringUtils.urlEncode(org)));
    builder.append("&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY&ps=500&p=");
    builder.append(page);
    return builder.toString();
  }

  private String getSearchBySeverityUrl(String severity, Set<String> enabledLanguageKeys, int page, int pageSize) {
    var builder = new StringBuilder(1024);
    builder.append(RULES_SEARCH_URL);
    helper.getOrganizationKey()
      .ifPresent(org -> builder.append("&organization=").append(StringUtils.urlEncode(org)));
    builder.append("&severities=").append(severity);
    builder.append("&languages=").append(String.join(",", enabledLanguageKeys));
    builder.append("&p=").append(page);
    builder.append("&ps=").append(pageSize);
    return builder.toString();
  }

  private static Rules.SearchResponse loadFromStream(HttpClient.Response response) {
    try (var toBeClosed = response; InputStream is = toBeClosed.bodyAsStream()) {
      return Rules.SearchResponse.parseFrom(is);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load rules", e);
    }
  }

  private static void readPage(Map<RuleKey, ServerRules.Rule> rulesByKey, Map<String, List<ServerRules.ActiveRule>> activeRulesByQProfileKey, Rules.SearchResponse response) {
    for (var r : response.getRulesList()) {
      var ruleKey = RuleKey.parse(r.getKey());
      if (rulesByKey.containsKey(ruleKey)) {
        // XXX do we really need to check this ? is it normal that some rule keys are duplicated in test data ?
        continue;
      }
      rulesByKey.put(ruleKey, new ServerRules.Rule(
        ruleKey.repository(),
        ruleKey.rule(),
        r.getName(),
        r.getSeverity(),
        r.getLang(),
        r.getInternalKey(),
        r.getHtmlDesc(),
        r.getHtmlNote(),
        r.getIsTemplate(),
        r.getTemplateKey(),
        typeToString(r.getType())));
    }
    for (var entry : response.getActives().getActives().entrySet()) {
      var ruleKey = RuleKey.parse(entry.getKey());
      for (Rules.Active ar : entry.getValue().getActiveListList()) {
        String qProfileKey = ar.getQProfile();
        var activeRule = new ServerRules.ActiveRule(
          ruleKey,
          ar.getSeverity(),
          ar.getParamsList().stream().map(p -> new ServerRules.ActiveRule.Param(p.getKey(), p.getValue())).collect(Collectors.toList()));
        activeRulesByQProfileKey.computeIfAbsent(qProfileKey, k -> new ArrayList<>()).add(activeRule);
      }
    }

    for (var entry : response.getQProfiles().getQProfiles().entrySet()) {
      if (!activeRulesByQProfileKey.containsKey(entry.getValue().getName())) {
        activeRulesByQProfileKey.put(entry.getValue().getName(), Collections.emptyList());
      }
    }
  }

  @CheckForNull
  private static String typeToString(Common.RuleType type) {
    switch (type) {
      case BUG:
      case CODE_SMELL:
      case VULNERABILITY:
        return type.toString();
      case UNKNOWN:
      default:
        return null;
    }
  }
}
