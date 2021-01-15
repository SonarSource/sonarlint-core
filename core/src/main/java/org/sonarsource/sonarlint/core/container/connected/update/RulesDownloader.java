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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.rule.RuleKey;
import org.sonarqube.ws.Common.RuleType;
import org.sonarqube.ws.Rules.Active.Param;
import org.sonarqube.ws.Rules.ActiveList;
import org.sonarqube.ws.Rules.Rule;
import org.sonarqube.ws.Rules.SearchResponse;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.http.SonarLintHttpClient;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules.Rule.Builder;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

import static org.sonarsource.sonarlint.core.container.storage.StoragePaths.encodeForFs;

public class RulesDownloader {
  static final String RULES_SEARCH_URL = "/api/rules/search.protobuf?f=repo,name,severity,lang,htmlDesc,htmlNote,internalKey,isTemplate,templateKey,"
    + "actives&statuses=BETA,DEPRECATED,READY&types=CODE_SMELL,BUG,VULNERABILITY";

  private final SonarLintWsClient wsClient;

  public RulesDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public void fetchRulesTo(Path destDir, ProgressWrapper progress) {

    Rules.Builder rulesBuilder = Rules.newBuilder();
    Map<String, ActiveRules.Builder> activeRulesBuildersByQProfile = new HashMap<>();

    for (int i = 0; i < Severity.values().length; i++) {
      Severity severity = Severity.values()[i];
      progress.setProgressAndCheckCancel("Loading severity '" + severity.name().toLowerCase(Locale.US) + "'",
        i / (float) Severity.values().length);
      ProgressWrapper severityProgress = progress.subProgress(i / (float) Severity.values().length,
        (i + 1) / (float) Severity.values().length, severity.name().toLowerCase(Locale.US));
      fetchRulesAndActiveRules(rulesBuilder, severity.name(), activeRulesBuildersByQProfile, severityProgress);
    }
    Path activeRulesDir = destDir.resolve(StoragePaths.ACTIVE_RULES_FOLDER);
    FileUtils.mkdirs(activeRulesDir);
    for (Map.Entry<String, ActiveRules.Builder> entry : activeRulesBuildersByQProfile.entrySet()) {
      ProtobufUtil.writeToFile(entry.getValue().build(), activeRulesDir.resolve(encodeForFs(entry.getKey()) + ".pb"));
    }

    ProtobufUtil.writeToFile(rulesBuilder.build(), destDir.resolve(StoragePaths.RULES_PB));
  }

  private void fetchRulesAndActiveRules(Rules.Builder rulesBuilder, String severity, Map<String, ActiveRules.Builder> activeRulesBuildersByQProfile, ProgressWrapper progress) {
    int page = 0;
    int pageSize = 500;
    int loaded = 0;

    while (true) {
      page++;
      SearchResponse response = loadFromStream(wsClient.get(getUrl(severity, page, pageSize)));
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

  private String getUrl(String severity, int page, int pageSize) {
    StringBuilder builder = new StringBuilder(1024);
    builder.append(RULES_SEARCH_URL);
    wsClient.getOrganizationKey()
      .ifPresent(org -> builder.append("&organization=").append(StringUtils.urlEncode(org)));
    builder.append("&severities=").append(severity);
    builder.append("&p=").append(page);
    builder.append("&ps=").append(pageSize);
    return builder.toString();
  }

  private static SearchResponse loadFromStream(SonarLintHttpClient.Response response) {
    try (SonarLintHttpClient.Response toBeClosed = response; InputStream is = toBeClosed.bodyAsStream()) {
      return SearchResponse.parseFrom(is);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load rules", e);
    }
  }

  private static void readPage(Rules.Builder rulesBuilder, Map<String, ActiveRules.Builder> activeRulesBuildersByQProfile, SearchResponse response) {
    Builder ruleBuilder = Rules.Rule.newBuilder();
    for (Rule r : response.getRulesList()) {
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
    ActiveRules.ActiveRule.Builder arBuilder = ActiveRules.ActiveRule.newBuilder();
    for (Map.Entry<String, ActiveList> entry : response.getActives().getActives().entrySet()) {
      RuleKey ruleKey = RuleKey.parse(entry.getKey());
      for (org.sonarqube.ws.Rules.Active ar : entry.getValue().getActiveListList()) {
        String qProfileKey = ar.getQProfile();
        if (!activeRulesBuildersByQProfile.containsKey(qProfileKey)) {
          activeRulesBuildersByQProfile.put(qProfileKey, ActiveRules.newBuilder());
        }
        arBuilder.clear();
        arBuilder.setRepo(ruleKey.repository());
        arBuilder.setKey(ruleKey.rule());
        arBuilder.setSeverity(ar.getSeverity());
        for (Param p : ar.getParamsList()) {
          arBuilder.putParams(p.getKey(), p.getValue());
        }
        activeRulesBuildersByQProfile.get(qProfileKey).putActiveRulesByKey(entry.getKey(), arBuilder.build());
      }
    }

    for (Entry<String, org.sonarqube.ws.Rules.QProfile> entry : response.getQProfiles().getQProfiles().entrySet()) {
      if (!activeRulesBuildersByQProfile.containsKey(entry.getValue().getName())) {
        activeRulesBuildersByQProfile.put(entry.getValue().getName(), ActiveRules.newBuilder());
      }
    }
  }

  @CheckForNull
  private static String typeToString(RuleType type) {
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
