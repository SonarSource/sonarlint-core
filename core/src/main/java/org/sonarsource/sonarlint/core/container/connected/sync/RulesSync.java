/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.sync;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.sonar.api.rule.RuleKey;
import org.sonarqube.ws.QualityProfiles;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse.QualityProfile;
import org.sonarqube.ws.Rules.Active.Param;
import org.sonarqube.ws.Rules.ActiveList;
import org.sonarqube.ws.Rules.Rule;
import org.sonarqube.ws.Rules.SearchResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules.QProfile;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules.Rule.Builder;
import org.sonarsource.sonarlint.core.util.FileUtils;

public class RulesSync {
  private static final String RULES_SEARCH_URL =
    "/api/rules/search.protobuf?f=repo,name,severity,lang,internalKey,isTemplate,templateKey,htmlDesc,mdDesc,actives&statuses=BETA,DEPRECATED,READY";
  private static final String DEFAULT_QP_SEARCH_URL = "/api/qualityprofiles/search.protobuf?defaults=true";

  private final SonarLintWsClient wsClient;

  public RulesSync(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public void fetchRulesTo(Path destDir) {
    Rules.Builder rulesBuilder = Rules.newBuilder();
    Map<String, ActiveRules.Builder> activeRulesBuildersByQProfile = new HashMap<>();
    fetchRulesAndActiveRules(rulesBuilder, activeRulesBuildersByQProfile);
    Path activeRulesDir = destDir.resolve(StorageManager.ACTIVE_RULES_FOLDER);
    FileUtils.forceMkDirs(activeRulesDir);
    for (Map.Entry<String, ActiveRules.Builder> entry : activeRulesBuildersByQProfile.entrySet()) {
      rulesBuilder.getMutableQprofilesByKey().put(entry.getKey(), QProfile.newBuilder().setKey(entry.getKey()).build());
      ProtobufUtil.writeToFile(entry.getValue().build(), activeRulesDir.resolve(entry.getKey() + ".pb"));
    }

    InputStream contentStream = wsClient.get(DEFAULT_QP_SEARCH_URL).contentStream();
    try {
      SearchWsResponse qpResponse = QualityProfiles.SearchWsResponse.parseFrom(contentStream);
      for (QualityProfile qp : qpResponse.getProfilesList()) {
        rulesBuilder.getMutableDefaultQProfilesByLanguage().put(qp.getLanguage(), qp.getKey());
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load default quality profiles", e);
    } finally {
      IOUtils.closeQuietly(contentStream);
    }

    ProtobufUtil.writeToFile(rulesBuilder.build(), destDir.resolve(StorageManager.RULES_PB));
  }

  private void fetchRulesAndActiveRules(Rules.Builder rulesBuilder, Map<String, ActiveRules.Builder> activeRulesBuildersByQProfile) {
    int page = 1;
    int pageSize = 500;
    int loaded = 0;

    while (true) {
      SearchResponse response = loadFromStream(wsClient.get(getUrl(page, pageSize)).contentStream());
      readPage(rulesBuilder, activeRulesBuildersByQProfile, response);
      loaded += response.getPs();

      if (response.getTotal() <= loaded) {
        break;
      }
      page++;
    }
  }

  private static String getUrl(int page, int pageSize) {
    StringBuilder builder = new StringBuilder(1024);
    builder.append(RULES_SEARCH_URL);
    builder.append("&p=").append(page);
    builder.append("&ps=").append(pageSize);
    return builder.toString();
  }

  private static SearchResponse loadFromStream(InputStream is) {
    try {
      return SearchResponse.parseFrom(is);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load rules", e);
    } finally {
      IOUtils.closeQuietly(is);
    }
  }

  private static void readPage(Rules.Builder rulesBuilder, Map<String, ActiveRules.Builder> activeRulesBuildersByQProfile, SearchResponse response) {
    Builder ruleBuilder = Rules.Rule.newBuilder();
    for (Rule r : response.getRulesList()) {
      ruleBuilder.clear();
      RuleKey ruleKey = RuleKey.parse(r.getKey());
      rulesBuilder.getMutableRulesByKey().put(r.getKey(), ruleBuilder
        .setRepo(ruleKey.repository())
        .setKey(ruleKey.rule())
        .setName(r.getName())
        .setSeverity(r.getSeverity())
        .setLang(r.getLang())
        .setInternalKey(r.getInternalKey())
        .setHtmlDesc(r.getHtmlDesc())
        .setIsTemplate(r.getIsTemplate())
        .setTemplateKey(r.getTemplateKey())
        .build());
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
          arBuilder.getMutableParams().put(p.getKey(), p.getValue());
        }
        activeRulesBuildersByQProfile.get(qProfileKey).getMutableActiveRulesByKey().put(entry.getKey(), arBuilder.build());
      }

    }
  }
}
