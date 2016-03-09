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
package org.sonarsource.sonarlint.core.container.standalone.rule;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.picocontainer.injectors.ProviderAdapter;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.rule.internal.NewActiveRule;
import org.sonar.api.profiles.ProfileDefinition;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.ActiveRuleParam;
import org.sonar.api.utils.ValidationMessages;

/**
 * Loads the rules that are activated on the Quality profiles
 * used by the current project and builds {@link org.sonar.api.batch.rule.ActiveRules}.
 */
public class StandaloneActiveRulesProvider extends ProviderAdapter {
  private ActiveRules singleton = null;

  public ActiveRules provide(ProfileDefinition[] profileDefinitions) {
    if (singleton == null) {
      ActiveRulesBuilder builder = new ActiveRulesBuilder();

      ListMultimap<String, RulesProfile> profilesByLanguage = profilesByLanguage(profileDefinitions);
      for (String language : profilesByLanguage.keySet()) {
        List<RulesProfile> defs = profilesByLanguage.get(language);
        registerProfilesForLanguage(builder, language, defs);
      }

      singleton = builder.build();

    }
    return singleton;
  }

  private static void registerProfilesForLanguage(ActiveRulesBuilder builder, String language, List<RulesProfile> defs) {
    for (Map.Entry<String, Collection<RulesProfile>> entry : profilesByName(defs).entrySet()) {
      String name = entry.getKey();
      if ("Sonar way".equals(name)) {
        for (RulesProfile rp : entry.getValue()) {
          for (ActiveRule ar : rp.getActiveRules()) {
            NewActiveRule newAr = builder.create(RuleKey.of(ar.getRepositoryKey(), ar.getRuleKey()))
              .setLanguage(language)
              .setName(ar.getRule().getName())
              .setSeverity(ar.getSeverity().name())
              .setInternalKey(ar.getConfigKey())
              .setTemplateRuleKey(ar.getRule().getKey());
            for (ActiveRuleParam param : ar.getActiveRuleParams()) {
              newAr.setParam(param.getKey(), param.getValue());
            }
            newAr.activate();
          }
        }
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
    return Multimaps.index(profiles, new Function<RulesProfile, String>() {
      @Override
      public String apply(@Nullable RulesProfile profile) {
        return profile != null ? profile.getName() : null;
      }
    }).asMap();
  }
}
