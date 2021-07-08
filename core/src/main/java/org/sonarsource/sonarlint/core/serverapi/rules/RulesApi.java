/*
 * SonarLint Core - Implementation
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.rule.RuleKey;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

import static java.util.stream.Collectors.joining;

public class RulesApi {
  public static final String RULES_SEARCH_URL = "/api/rules/search.protobuf?f=repo,name,severity,lang,htmlDesc,htmlNote,internalKey,isTemplate,templateKey,"
    + "actives&statuses=BETA,DEPRECATED,READY&types=CODE_SMELL,BUG,VULNERABILITY";

  private final ServerApiHelper helper;

  public RulesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public ServerRules getAll(Set<Language> enabledLanguages, ProgressWrapper progress) {
    Sonarlint.Rules.Builder rulesBuilder = Sonarlint.Rules.newBuilder();
    Map<String, Sonarlint.ActiveRules.Builder> activeRulesBuildersByQProfile = new HashMap<>();

    for (int i = 0; i < Severity.values().length; i++) {
      Severity severity = Severity.values()[i];
      progress.setProgressAndCheckCancel("Loading severity '" + severity.name().toLowerCase(Locale.US) + "'",
        i / (float) Severity.values().length);
      ProgressWrapper severityProgress = progress.subProgress(i / (float) Severity.values().length,
        (i + 1) / (float) Severity.values().length, severity.name().toLowerCase(Locale.US));
      fetchRulesAndActiveRules(rulesBuilder, severity.name(), activeRulesBuildersByQProfile, enabledLanguages, severityProgress);
    }
    Map<String, Sonarlint.ActiveRules> activeRulesByQualityProfileKey = activeRulesBuildersByQProfile.entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().build()));
    return new ServerRules(rulesBuilder.build(), activeRulesByQualityProfileKey);
  }

  private void fetchRulesAndActiveRules(Sonarlint.Rules.Builder rulesBuilder, String severity, Map<String, Sonarlint.ActiveRules.Builder> activeRulesBuildersByQProfile,
    Set<Language> enabledLanguages, ProgressWrapper progress) {
    int page = 0;
    int pageSize = 500;
    int loaded = 0;

    while (true) {
      page++;
      Rules.SearchResponse response = loadFromStream(helper.get(getUrl(severity, enabledLanguages, page, pageSize)));
      if (response.getTotal() > 10_000) {
        throw new IllegalStateException(
          String.format("Found more than 10000 rules for severity '%s' in the SonarQube server, which is not supported by SonarLint.", severity));
      }
      readPage(rulesBuilder, activeRulesBuildersByQProfile, response);
      loaded += response.getPs();

      if (response.getTotal() <= loaded) {
        break;
      }
      progress.setProgressAndCheckCancel("Loading page " + page, loaded / (float) response.getTotal());
    }
  }

  private String getUrl(String severity, Set<Language> enabledLanguages, int page, int pageSize) {
    StringBuilder builder = new StringBuilder(1024);
    builder.append(RULES_SEARCH_URL);
    helper.getOrganizationKey()
      .ifPresent(org -> builder.append("&organization=").append(StringUtils.urlEncode(org)));
    builder.append("&severities=").append(severity);
    builder.append("&languages=").append(enabledLanguages.stream().map(Language::getLanguageKey).collect(joining(",")));
    builder.append("&p=").append(page);
    builder.append("&ps=").append(pageSize);
    return builder.toString();
  }

  private static Rules.SearchResponse loadFromStream(HttpClient.Response response) {
    try (HttpClient.Response toBeClosed = response; InputStream is = toBeClosed.bodyAsStream()) {
      return Rules.SearchResponse.parseFrom(is);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load rules", e);
    }
  }

  private static void readPage(Sonarlint.Rules.Builder rulesBuilder, Map<String, Sonarlint.ActiveRules.Builder> activeRulesBuildersByQProfile, Rules.SearchResponse response) {
    Sonarlint.Rules.Rule.Builder ruleBuilder = Sonarlint.Rules.Rule.newBuilder();
    for (Rules.Rule r : response.getRulesList()) {
      RuleKey ruleKey = RuleKey.parse(r.getKey());

      ruleBuilder.clear();
      ruleBuilder
        .setRepo(ruleKey.repository())
        .setKey(ruleKey.rule())
        .setName(r.getName())
        .setSeverity(r.getSeverity())
        .setLang(r.getLang())
        .setInternalKey(r.getInternalKey())
        .setHtmlDesc(r.getHtmlDesc())
        .setHtmlNote(r.getHtmlNote())
        .setIsTemplate(r.getIsTemplate())
        .setTemplateKey(r.getTemplateKey());

      String type = typeToString(r.getType());
      if (type != null) {
        ruleBuilder.setType(type);
      }

      rulesBuilder.putRulesByKey(r.getKey(), ruleBuilder.build());
    }
    Sonarlint.ActiveRules.ActiveRule.Builder arBuilder = Sonarlint.ActiveRules.ActiveRule.newBuilder();
    for (Map.Entry<String, Rules.ActiveList> entry : response.getActives().getActives().entrySet()) {
      RuleKey ruleKey = RuleKey.parse(entry.getKey());
      for (Rules.Active ar : entry.getValue().getActiveListList()) {
        String qProfileKey = ar.getQProfile();
        if (!activeRulesBuildersByQProfile.containsKey(qProfileKey)) {
          activeRulesBuildersByQProfile.put(qProfileKey, Sonarlint.ActiveRules.newBuilder());
        }
        arBuilder.clear();
        arBuilder.setRepo(ruleKey.repository());
        arBuilder.setKey(ruleKey.rule());
        arBuilder.setSeverity(ar.getSeverity());
        for (Rules.Active.Param p : ar.getParamsList()) {
          arBuilder.putParams(p.getKey(), p.getValue());
        }
        activeRulesBuildersByQProfile.get(qProfileKey).putActiveRulesByKey(entry.getKey(), arBuilder.build());
      }
    }

    for (Map.Entry<String, Rules.QProfile> entry : response.getQProfiles().getQProfiles().entrySet()) {
      if (!activeRulesBuildersByQProfile.containsKey(entry.getValue().getName())) {
        activeRulesBuildersByQProfile.put(entry.getValue().getName(), Sonarlint.ActiveRules.newBuilder());
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
