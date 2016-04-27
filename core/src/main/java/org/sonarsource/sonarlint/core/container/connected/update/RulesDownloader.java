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
package org.sonarsource.sonarlint.core.container.connected.update;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.sonar.api.internal.apachecommons.lang.StringUtils;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonarqube.ws.Rules.Active;
import org.sonarqube.ws.Rules.Active.Param;
import org.sonarqube.ws.Rules.ActiveList;
import org.sonarqube.ws.Rules.Rule;
import org.sonarqube.ws.Rules.SearchResponse;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules.Rule.Builder;
import org.sonarsource.sonarlint.core.util.FileUtils;

public class RulesDownloader {
  private static final org.sonarqube.ws.Rules.Active.Param.Builder PARAM_BUILDER = Param.newBuilder();
  private static final org.sonarqube.ws.Rules.Active.Builder AR_BUILDER = Active.newBuilder();
  private static final org.sonarqube.ws.Rules.ActiveList.Builder ARL_BUILDER = ActiveList.newBuilder();
  private static final org.sonarqube.ws.Rules.Rule.Builder RULE_BUILDER = Rule.newBuilder();
  private static final String RULES_SEARCH_URL = "/api/rules/search.protobuf?f=repo,name,severity,lang,internalKey,isTemplate,templateKey,"
    + "actives&statuses=BETA,DEPRECATED,READY";
  private static final String RULES_SEARCH_URL_JSON = "/api/rules/search?f=repo,name,severity,lang,internalKey,isTemplate,templateKey,"
    + "actives&statuses=BETA,DEPRECATED,READY";

  private final SonarLintWsClient wsClient;
  private final RulesDefinitionsLoader rulesDefinitionsLoader;

  public RulesDownloader(SonarLintWsClient wsClient, RulesDefinitionsLoader rulesDefinitionsLoader) {
    this.wsClient = wsClient;
    this.rulesDefinitionsLoader = rulesDefinitionsLoader;
  }

  public void fetchRulesTo(Path destDir, String serverVersionStr, PluginReferences pluginReferences) {
    Version serverVersion = Version.create(serverVersionStr);
    Rules.Builder rulesBuilder = Rules.newBuilder();
    Map<String, ActiveRules.Builder> activeRulesBuildersByQProfile = new HashMap<>();
    fetchRulesAndActiveRules(rulesBuilder, activeRulesBuildersByQProfile, serverVersion, rulesDefinitionsLoader.loadRuleDefinitions(pluginReferences));
    Path activeRulesDir = destDir.resolve(StorageManager.ACTIVE_RULES_FOLDER);
    FileUtils.forceMkDirs(activeRulesDir);
    for (Map.Entry<String, ActiveRules.Builder> entry : activeRulesBuildersByQProfile.entrySet()) {
      ProtobufUtil.writeToFile(entry.getValue().build(), activeRulesDir.resolve(StorageManager.encodeForFs(entry.getKey()) + ".pb"));
    }

    ProtobufUtil.writeToFile(rulesBuilder.build(), destDir.resolve(StorageManager.RULES_PB));
  }

  private void fetchRulesAndActiveRules(Rules.Builder rulesBuilder, Map<String, ActiveRules.Builder> activeRulesBuildersByQProfile, Version serverVersion,
    Context rulesDefinitions) {
    int page = 1;
    int pageSize = 500;
    int loaded = 0;

    while (true) {
      SearchResponse response = loadFromStream(wsClient.get(getUrl(page, pageSize, serverVersion)), serverVersion);
      readPage(rulesBuilder, activeRulesBuildersByQProfile, response, rulesDefinitions);
      loaded += response.getPs();

      if (response.getTotal() <= loaded) {
        break;
      }
      page++;
    }
  }

  private static String getUrl(int page, int pageSize, Version serverVersion) {
    StringBuilder builder = new StringBuilder(1024);
    builder.append(supportProtobuf(serverVersion) ? RULES_SEARCH_URL : RULES_SEARCH_URL_JSON);
    builder.append("&p=").append(page);
    builder.append("&ps=").append(pageSize);
    return builder.toString();
  }

  private static SearchResponse loadFromStream(WsResponse response, Version serverVersion) {
    if (!supportProtobuf(serverVersion)) {
      return loadFromJson(response);
    } else {
      try (InputStream is = response.contentStream()) {
        return SearchResponse.parseFrom(is);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to load rules", e);
      }
    }
  }

  private static SearchResponse loadFromJson(WsResponse response) {
    SearchResponse.Builder builder = SearchResponse.newBuilder();
    try (JsonReader reader = new JsonReader(response.contentReader())) {
      reader.beginObject();
      while (reader.hasNext()) {
        String propName = reader.nextName();
        switch (propName) {
          case "total":
            builder.setTotal(reader.nextInt());
            break;
          case "ps":
            builder.setPs(reader.nextInt());
            break;
          case "rules":
            parseJsonRules(builder, reader);
            break;
          case "actives":
            parseActivesRules(builder, reader);
            break;
          default:
            reader.skipValue();
        }
      }
      reader.endObject();
      return builder.build();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to parse global properties from: " + response.content(), e);
    }
  }

  private static void parseJsonRules(SearchResponse.Builder builder, JsonReader reader) throws IOException {
    reader.beginArray();
    while (reader.hasNext()) {
      builder.addRules(parseJsonRule(reader));
    }
    reader.endArray();
  }

  private static void parseActivesRules(SearchResponse.Builder builder, JsonReader reader) throws IOException {
    org.sonarqube.ws.Rules.Actives.Builder activesBuilder = builder.getActivesBuilder();
    reader.beginObject();
    while (reader.hasNext()) {
      String ruleKey = reader.nextName();
      activesBuilder.getMutableActives().put(ruleKey, parseActiveList(reader));
    }
    reader.endObject();
    builder.setActives(activesBuilder);
  }

  private static ActiveList parseActiveList(JsonReader reader) throws IOException {
    ARL_BUILDER.clear();
    reader.beginArray();
    while (reader.hasNext()) {
      AR_BUILDER.clear();
      reader.beginObject();
      while (reader.hasNext()) {
        String propName = reader.nextName();
        switch (propName) {
          case "qProfile":
            AR_BUILDER.setQProfile(reader.nextString());
            break;
          case "severity":
            AR_BUILDER.setSeverity(reader.nextString());
            break;
          case "params":
            parseParams(reader, AR_BUILDER);
            break;
          default:
            reader.skipValue();
        }
      }
      ARL_BUILDER.addActiveList(AR_BUILDER.build());
      reader.endObject();
    }
    reader.endArray();
    return ARL_BUILDER.build();
  }

  private static void parseParams(JsonReader reader, org.sonarqube.ws.Rules.Active.Builder arBuilder) throws IOException {
    reader.beginArray();
    while (reader.hasNext()) {
      PARAM_BUILDER.clear();
      reader.beginObject();
      String key = "";
      String value = "";
      while (reader.hasNext()) {
        String propName = reader.nextName();
        switch (propName) {
          case "key":
            key = reader.nextString();
            break;
          case "value":
            value = reader.nextString();
            break;
          default:
            reader.skipValue();
        }
      }
      arBuilder.addParams(PARAM_BUILDER.setKey(key).setValue(value));
      reader.endObject();
    }
    reader.endArray();
  }

  private static Rule parseJsonRule(JsonReader reader) throws IOException {
    RULE_BUILDER.clear();
    reader.beginObject();
    while (reader.hasNext()) {
      String propName = reader.nextName();
      switch (propName) {
        case "key":
          RULE_BUILDER.setKey(reader.nextString());
          break;
        case "severity":
          RULE_BUILDER.setSeverity(reader.nextString());
          break;
        case "isTemplate":
          RULE_BUILDER.setIsTemplate(reader.nextBoolean());
          break;
        case "repo":
          RULE_BUILDER.setRepo(reader.nextString());
          break;
        case "name":
          RULE_BUILDER.setName(reader.nextString());
          break;
        case "lang":
          RULE_BUILDER.setLang(reader.nextString());
          break;
        case "templateKey":
          RULE_BUILDER.setTemplateKey(reader.nextString());
          break;
        default:
          reader.skipValue();
      }
    }
    reader.endObject();
    return RULE_BUILDER.build();
  }

  private static boolean supportProtobuf(Version serverVersion) {
    return serverVersion.compareTo(Version.create("5.2")) >= 0;
  }

  private static void readPage(Rules.Builder rulesBuilder, Map<String, ActiveRules.Builder> activeRulesBuildersByQProfile, SearchResponse response, Context rulesDefinitions) {
    Builder ruleBuilder = Rules.Rule.newBuilder();
    for (Rule r : response.getRulesList()) {
      ruleBuilder.clear();
      RuleKey ruleKey = RuleKey.parse(r.getKey());
      String htmlDescription = getDescription(rulesDefinitions, ruleKey, r.getTemplateKey());
      rulesBuilder.getMutableRulesByKey().put(r.getKey(), ruleBuilder
        .setRepo(ruleKey.repository())
        .setKey(ruleKey.rule())
        .setName(r.getName())
        .setSeverity(r.getSeverity())
        .setLang(r.getLang())
        .setInternalKey(r.getInternalKey())
        .setHtmlDesc(htmlDescription)
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

    for (Entry<String, org.sonarqube.ws.Rules.QProfile> entry : response.getQProfiles().getQProfiles().entrySet()) {
      if (!activeRulesBuildersByQProfile.containsKey(entry.getValue().getName())) {
        activeRulesBuildersByQProfile.put(entry.getValue().getName(), ActiveRules.newBuilder());
      }
    }
  }

  private static String getDescription(Context rulesDefinitions, RuleKey ruleKey, String templateKey) {
    if (StringUtils.isNotEmpty(templateKey)) {
      // Description will be missing for custom rules based on rule templates
      return "Description not available for custom rules";
    }
    Repository repository = rulesDefinitions.repository(ruleKey.repository());
    if (repository == null) {
      // The rule is part of a plugin we don't have (probably blacklisted)
      return "Description not available";
    }
    org.sonar.api.server.rule.RulesDefinition.Rule rule = repository.rule(ruleKey.rule());
    // Should never happen
    return rule != null ? rule.htmlDescription() : "Description not available";
  }
}
