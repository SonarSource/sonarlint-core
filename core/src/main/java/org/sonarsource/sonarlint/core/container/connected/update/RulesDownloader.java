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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.storage.ActiveRulesStore;
import org.sonarsource.sonarlint.core.container.storage.RulesStore;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.rules.RulesApi;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class RulesDownloader {
  private final RulesApi rulesApi;
  private final Set<Language> enabledLanguages;
  private final RulesStore rulesStore;
  private final ActiveRulesStore activeRulesStore;

  public RulesDownloader(ServerApiHelper serverApiHelper, ConnectedGlobalConfiguration globalConfiguration, RulesStore rulesStore, ActiveRulesStore activeRulesStore) {
    this.rulesApi = new ServerApi(serverApiHelper).rules();
    this.enabledLanguages = globalConfiguration.getEnabledLanguages();
    this.rulesStore = rulesStore;
    this.activeRulesStore = activeRulesStore;
  }

  public List<UpdateEvent> fetchRules(ActiveRulesStore currentActiveRulesStore, RulesStore currentRulesStore, ProgressWrapper progress) {
    var serverRules = rulesApi.getAll(enabledLanguages, progress);
    Map<String, Sonarlint.ActiveRules> activeRulesByQualityProfile = serverRules.getActiveRulesByQualityProfile();
    List<UpdateEvent> ruleEvents = new ArrayList<>(diffActiveRules(activeRulesByQualityProfile, currentActiveRulesStore));
    activeRulesStore.store(activeRulesByQualityProfile);
    var previousRules = currentRulesStore.getAllOrEmpty();
    var newRules = serverRules.getAll();
    ruleEvents.addAll(diffRules(previousRules, newRules));
    rulesStore.store(newRules);
    return ruleEvents;
  }

  private static List<UpdateEvent> diffActiveRules(Map<String, Sonarlint.ActiveRules> activeRulesByQualityProfile, ActiveRulesStore activeRulesStore) {
    List<UpdateEvent> events = new ArrayList<>();
    for (Map.Entry<String, Sonarlint.ActiveRules> activeRulesByProfileKey : activeRulesByQualityProfile.entrySet()) {
      var oldActiveRules = activeRulesStore.getActiveRules(activeRulesByProfileKey.getKey());
      Map<String, Sonarlint.ActiveRules.ActiveRule> oldActiveRulesByKey = oldActiveRules.getActiveRulesByKeyMap();
      Map<String, Sonarlint.ActiveRules.ActiveRule> newActiveRulesByKey = activeRulesByProfileKey.getValue().getActiveRulesByKeyMap();
      for (Map.Entry<String, Sonarlint.ActiveRules.ActiveRule> newActiveRule : newActiveRulesByKey.entrySet()) {
        if (!oldActiveRulesByKey.containsKey(newActiveRule.getKey())) {
          events.add(new RuleActivated(activeRulesByProfileKey.getKey(), newActiveRule.getValue()));
        }
      }
      for (Map.Entry<String, Sonarlint.ActiveRules.ActiveRule> oldActiveRule : oldActiveRulesByKey.entrySet()) {
        if (!newActiveRulesByKey.containsKey(oldActiveRule.getKey())) {
          events.add(new RuleDeactivated(activeRulesByProfileKey.getKey(), oldActiveRule.getValue()));
        }
      }
    }

    return events;
  }

  private static List<UpdateEvent> diffRules(Sonarlint.Rules previousRules, Sonarlint.Rules newRules) {
    ArrayList<UpdateEvent> events = new ArrayList<>();
    Map<String, Sonarlint.Rules.Rule> previousRulesByKey = previousRules.getRulesByKeyMap();
    Map<String, Sonarlint.Rules.Rule> newRulesByKey = newRules.getRulesByKeyMap();
    for (Map.Entry<String, Sonarlint.Rules.Rule> newRule : newRulesByKey.entrySet()) {
      String ruleKey = newRule.getKey();
      var rule = newRule.getValue();
      if (previousRulesByKey.containsKey(ruleKey)) {
        if (!previousRulesByKey.get(ruleKey).equals(rule)) {
          events.add(new RuleMetadataUpdated(rule));
        }
      } else {
        events.add(new RuleAdded(rule));
      }
    }
    for (Map.Entry<String, Sonarlint.Rules.Rule> oldRule : previousRulesByKey.entrySet()) {
      if (!newRulesByKey.containsKey(oldRule.getKey())) {
        events.add(new RuleDeleted(oldRule.getValue()));
      }
    }

    return events;
  }

  public static class RuleEvent implements UpdateEvent {
    private final Sonarlint.Rules.Rule rule;

    protected RuleEvent(Sonarlint.Rules.Rule rule) {
      this.rule = rule;
    }

    public Sonarlint.Rules.Rule getRule() {
      return rule;
    }
  }

  public static class RuleMetadataUpdated extends RuleEvent {
    public RuleMetadataUpdated(Sonarlint.Rules.Rule rule) {
      super(rule);
    }
  }

  public static class RuleAdded extends RuleEvent {
    public RuleAdded(Sonarlint.Rules.Rule rule) {
      super(rule);
    }
  }

  public static class RuleDeleted extends RuleEvent {
    public RuleDeleted(Sonarlint.Rules.Rule rule) {
      super(rule);
    }
  }

  public static class ActiveRuleEvent implements UpdateEvent {
    private final String qualityProfileKey;
    private final Sonarlint.ActiveRules.ActiveRule activeRule;

    protected ActiveRuleEvent(String qualityProfileKey, Sonarlint.ActiveRules.ActiveRule activeRule) {
      this.qualityProfileKey = qualityProfileKey;
      this.activeRule = activeRule;
    }

    public String getQualityProfileKey() {
      return qualityProfileKey;
    }

    public Sonarlint.ActiveRules.ActiveRule getActiveRule() {
      return activeRule;
    }
  }

  public static class RuleActivated extends ActiveRuleEvent {
    protected RuleActivated(String qualityProfileKey, Sonarlint.ActiveRules.ActiveRule rule) {
      super(qualityProfileKey, rule);
    }
  }

  public static class RuleDeactivated extends ActiveRuleEvent {
    protected RuleDeactivated(String qualityProfileKey, Sonarlint.ActiveRules.ActiveRule rule) {
      super(qualityProfileKey, rule);
    }
  }
}
