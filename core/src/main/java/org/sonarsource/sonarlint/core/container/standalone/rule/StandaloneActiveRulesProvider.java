/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.standalone.rule;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.rule.internal.NewActiveRule;
import org.sonar.api.profiles.ProfileDefinition;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.ActiveRuleParam;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition.Param;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonar.api.utils.ValidationMessages;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.container.model.DefaultRuleDetails;

/**
 * Loads the rules that are activated on the Quality profiles
 * used by the current project and builds {@link org.sonar.api.batch.rule.ActiveRules}.
 */
public class StandaloneActiveRulesProvider {
  private StandaloneActiveRules singleton = null;
  private final StandaloneRuleDefinitionsLoader ruleDefsLoader;
  private final ProfileDefinition[] profileDefinitions;

  public StandaloneActiveRulesProvider(StandaloneRuleDefinitionsLoader ruleDefsLoader, ProfileDefinition[] profileDefinitions) {
    this.ruleDefsLoader = ruleDefsLoader;
    this.profileDefinitions = profileDefinitions;
  }

  public StandaloneActiveRulesProvider(StandaloneRuleDefinitionsLoader ruleDefsLoader) {
    this(ruleDefsLoader, new ProfileDefinition[0]);
  }

  public StandaloneActiveRules provide() {
    if (singleton == null) {
      singleton = createActiveRules();
    }
    return singleton;
  }

  private StandaloneActiveRules createActiveRules() {
    ActiveRulesBuilder activeBuilder = new ActiveRulesBuilder();
    ActiveRulesBuilder inactiveBuilder = new ActiveRulesBuilder();

    ListMultimap<String, RulesProfile> profilesByLanguage = profilesByLanguage(profileDefinitions);
    for (String language : profilesByLanguage.keySet()) {
      List<RulesProfile> defs = profilesByLanguage.get(language);
      registerProfilesForLanguage(activeBuilder, language, defs);
    }

    Map<String, RuleDetails> ruleDetailsMap = new HashMap<>();

    for (Repository repo : ruleDefsLoader.getContext().repositories()) {
      for (Rule rule : repo.rules()) {
        if (rule.type() == RuleType.SECURITY_HOTSPOT) {
          continue;
        }
        ActiveRulesBuilder builder = rule.activatedByDefault() ? activeBuilder : inactiveBuilder;
        RuleKey ruleKey = RuleKey.of(repo.key(), rule.key());
        NewActiveRule newAr = builder.create(ruleKey)
          .setLanguage(repo.language())
          .setName(rule.name())
          .setSeverity(rule.severity())
          .setInternalKey(rule.internalKey());
        for (Param param : rule.params()) {
          newAr.setParam(param.key(), param.defaultValue());
        }
        newAr.activate();

        DefaultRuleDetails ruleDetails = new DefaultRuleDetails(ruleKey.toString(), rule.name(), rule.htmlDescription(),
          rule.severity(), rule.type().name(), repo.language(), rule.tags(), "",
          rule.activatedByDefault());
        ruleDetailsMap.put(ruleKey.toString(), ruleDetails);
      }
    }

    return new StandaloneActiveRules(activeBuilder.build(), inactiveBuilder.build(), ruleDetailsMap);
  }

  private static void registerProfilesForLanguage(ActiveRulesBuilder builder, String language, List<RulesProfile> defs) {
    for (Map.Entry<String, Collection<RulesProfile>> entry : profilesByName(defs).entrySet()) {
      String name = entry.getKey();
      if ("Sonar way".equals(name)) {
        registerProfile(builder, language, entry);
      }
    }
  }

  private static void registerProfile(ActiveRulesBuilder builder, String language, Map.Entry<String, Collection<RulesProfile>> entry) {
    for (RulesProfile rp : entry.getValue()) {
      for (ActiveRule ar : rp.getActiveRules()) {
        NewActiveRule newAr = builder.create(RuleKey.of(ar.getRepositoryKey(), ar.getRuleKey()))
          .setLanguage(language)
          .setName(ar.getRule().getName())
          .setSeverity(ar.getSeverity().name())
          .setInternalKey(ar.getConfigKey());
        for (ActiveRuleParam param : ar.getActiveRuleParams()) {
          newAr.setParam(param.getKey(), param.getValue());
        }
        newAr.activate();
      }
    }
  }

  private static ListMultimap<String, RulesProfile> profilesByLanguage(ProfileDefinition[] profileDefinitions) {
    ListMultimap<String, RulesProfile> byLang = ArrayListMultimap.create();
    for (ProfileDefinition definition : profileDefinitions) {
      ValidationMessages validation = ValidationMessages.create();
      RulesProfile profile = definition.createProfile(validation);
      if (profile != null && !validation.hasErrors()) {
        byLang.put(StringUtils.lowerCase(profile.getLanguage()), profile);
      }
    }
    return byLang;
  }

  private static Map<String, Collection<RulesProfile>> profilesByName(List<RulesProfile> profiles) {
    return Multimaps.index(profiles, profile -> profile != null ? profile.getName() : null).asMap();
  }
}
