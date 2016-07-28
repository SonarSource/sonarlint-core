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
package org.sonarsource.sonarlint.core.container.storage;

import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;
import org.picocontainer.injectors.ProviderAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Rule;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.rule.internal.NewActiveRule;
import org.sonar.api.resources.Languages;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.container.connected.update.GlobalUpdateExecutor;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules.ActiveRule;
import org.sonarsource.sonarlint.core.proto.Sonarlint.QProfiles.QProfile;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;

public class SonarQubeActiveRulesProvider extends ProviderAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(SonarQubeActiveRulesProvider.class);

  private ActiveRules activeRules;

  public ActiveRules provide(Sonarlint.Rules storageRules, Sonarlint.QProfiles qProfiles, StorageManager storageManager, Rules rules,
    ConnectedAnalysisConfiguration analysisConfiguration, Languages languages) {
    if (activeRules == null) {

      ServerInfos serverInfos = storageManager.readServerInfosFromStorage();
      boolean supportQualityProfilesWS = GlobalUpdateExecutor.supportQualityProfilesWS(serverInfos.getVersion());

      Map<String, String> qProfilesByLanguage = loadQualityProfilesFromStorage(qProfiles, storageManager, analysisConfiguration.moduleKey(), supportQualityProfilesWS);

      ActiveRulesBuilder builder = new ActiveRulesBuilder();
      for (Map.Entry<String, String> entry : qProfilesByLanguage.entrySet()) {
        String language = entry.getKey();
        if (languages.get(language) == null) {
          continue;
        }

        String qProfileKey = entry.getValue();

        if (supportQualityProfilesWS) {
          QProfile qProfile = qProfiles.getQprofilesByKey().get(qProfileKey);

          if (qProfile.getActiveRuleCount() == 0) {
            LOG.debug("  * " + language + ": " + qProfileKey + " (0 rules)");
            continue;
          }
        }

        org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules activeRulesFromStorage = ProtobufUtil.readFile(storageManager.getActiveRulesPath(qProfileKey),
          Sonarlint.ActiveRules.parser());

        LOG.debug("  * " + language + ": " + qProfileKey + " (" + activeRulesFromStorage.getActiveRulesByKey().size() + " rules)");

        for (Map.Entry<String, ActiveRule> arEntry : activeRulesFromStorage.getActiveRulesByKey().entrySet()) {
          ActiveRule activeRule = arEntry.getValue();
          RuleKey ruleKey = RuleKey.of(activeRule.getRepo(), activeRule.getKey());
          Rule rule = rules.find(ruleKey);
          Sonarlint.Rules.Rule storageRule = storageRules.getRulesByKey().get(ruleKey.toString());
          NewActiveRule newActiveRule = builder.create(ruleKey)
            .setLanguage(language)
            .setName(rule.name())
            .setInternalKey(rule.internalKey())
            .setSeverity(activeRule.getSeverity());

          if (!StringUtils.isEmpty(storageRule.getTemplateKey())) {
            RuleKey templateRuleKey = RuleKey.parse(storageRule.getTemplateKey());
            newActiveRule.setTemplateRuleKey(templateRuleKey.rule());
          }

          for (Map.Entry<String, String> param : activeRule.getParams().entrySet()) {
            newActiveRule.setParam(param.getKey(), param.getValue());
          }
          newActiveRule.activate();
        }
      }

      activeRules = builder.build();
    }
    return activeRules;
  }

  private static Map<String, String> loadQualityProfilesFromStorage(Sonarlint.QProfiles qProfiles, StorageManager storageManager,
    @Nullable String moduleKey, boolean supportQualityProfilesWS) {
    Map<String, String> qProfilesByLanguage;
    if (moduleKey == null) {
      if (!supportQualityProfilesWS) {
        throw new UnsupportedOperationException("Unable to analyse a project with no key prior to SonarQube 5.2");
      }
      LOG.debug("Use default quality profiles:");
      qProfilesByLanguage = qProfiles.getDefaultQProfilesByLanguage();
    } else {
      LOG.debug("Quality profiles:");
      qProfilesByLanguage = storageManager.readModuleConfigFromStorage(moduleKey).getQprofilePerLanguage();
    }
    return qProfilesByLanguage;
  }

}
