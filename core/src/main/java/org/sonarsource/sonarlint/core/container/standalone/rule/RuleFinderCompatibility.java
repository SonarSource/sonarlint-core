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
import com.google.common.collect.Collections2;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleFinder;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.rules.RuleQuery;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Param;
import org.sonar.api.server.rule.RulesDefinition.Repository;

public class RuleFinderCompatibility implements RuleFinder {

  private static Function<RulesDefinition.Rule, Rule> ruleTransformer = new Function<RulesDefinition.Rule, Rule>() {
    @Override
    public Rule apply(@Nonnull RulesDefinition.Rule input) {
      return toRule(input);
    }
  };

  private final RulesDefinition.Context context;

  public RuleFinderCompatibility(StandalonePluginRulesLoader rules) {
    this.context = rules.getContext();
  }

  @Override
  public Rule findById(int ruleId) {
    throw new UnsupportedOperationException("Unable to find rule by id");
  }

  @Override
  public Rule findByKey(String repositoryKey, String key) {
    return findByKey(RuleKey.of(repositoryKey, key));
  }

  @Override
  public Rule findByKey(RuleKey key) {
    Repository repository = context.repository(key.repository());
    return repository != null ? toRule(repository.rule(key.rule())) : null;
  }

  @Override
  public Rule find(RuleQuery query) {
    Collection<Rule> all = findAll(query);
    if (all.size() > 1) {
      throw new IllegalArgumentException("Non unique result for rule query: " + ReflectionToStringBuilder.toString(query, ToStringStyle.SHORT_PREFIX_STYLE));
    } else if (all.isEmpty()) {
      return null;
    } else {
      return all.iterator().next();
    }
  }

  @Override
  public Collection<Rule> findAll(RuleQuery query) {
    if (query.getConfigKey() != null) {
      if (query.getRepositoryKey() != null && query.getKey() == null) {
        throw new UnsupportedOperationException("Unable to find rule by internal key");
      }
    } else if (query.getRepositoryKey() != null) {
      if (query.getKey() != null) {
        return byKey(query);
      } else {
        return byRepository(query);
      }
    }
    throw new UnsupportedOperationException("Unable to find rule by query");
  }

  private Collection<Rule> byRepository(RuleQuery query) {
    Repository repository = context.repository(query.getRepositoryKey());
    return repository != null ? Collections2.transform(repository.rules(), ruleTransformer) : Collections.<Rule>emptyList();
  }

  private Collection<Rule> byKey(RuleQuery query) {
    Rule rule = findByKey(query.getRepositoryKey(), query.getKey());
    return rule != null ? Arrays.asList(rule) : Collections.<Rule>emptyList();
  }

  @CheckForNull
  private static Rule toRule(@Nullable RulesDefinition.Rule ar) {
    return ar == null ? null : toRuleNotNull(ar);
  }

  private static Rule toRuleNotNull(RulesDefinition.Rule ruleDef) {
    Rule rule = Rule.create(ruleDef.repository().key(), ruleDef.key())
      .setName(ruleDef.name())
      .setSeverity(RulePriority.valueOf(ruleDef.severity()))
      .setLanguage(ruleDef.repository().language())
      .setIsTemplate(ruleDef.template())
      .setConfigKey(ruleDef.internalKey());
    for (Param param : ruleDef.params()) {
      rule.createParameter(param.key()).setDefaultValue(param.defaultValue()).setDescription(param.description());
    }
    return rule;
  }

}
