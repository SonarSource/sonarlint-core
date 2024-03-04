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
package org.sonarsource.sonarlint.core.container.standalone.rule;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.Immutable;
import org.sonar.api.batch.rule.RuleParam;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Param;
import org.sonar.markdown.Markdown;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintRule;

import static java.util.stream.Collectors.toList;

@Immutable
public class StandaloneRule implements SonarLintRule, StandaloneRuleDetails {

  private final RuleKey key;
  private final String name;
  private final String severity;
  private final RuleType type;
  private final String description;
  private final String internalKey;
  private final Map<String, DefaultStandaloneRuleParam> params;
  private final boolean isActiveByDefault;
  private final Language language;
  private final String[] tags;
  private final Set<RuleKey> deprecatedKeys;

  public StandaloneRule(RulesDefinition.Rule rule) {
    this.key = RuleKey.of(rule.repository().key(), rule.key());
    this.name = rule.name();
    this.severity = rule.severity();
    this.type = rule.type();
    this.description = rule.htmlDescription() != null ? rule.htmlDescription() : Markdown.convertToHtml(rule.markdownDescription());
    this.internalKey = rule.internalKey();
    this.isActiveByDefault = rule.activatedByDefault();
    this.language = Language.forKey(rule.repository().language()).orElseThrow(() -> new IllegalStateException("Unknown language with key: " + rule.repository().language()));
    this.tags = rule.tags().toArray(new String[0]);
    this.deprecatedKeys = rule.deprecatedRuleKeys();

    Map<String, DefaultStandaloneRuleParam> builder = new HashMap<>();
    for (Param param : rule.params()) {
      builder.put(param.key(), new DefaultStandaloneRuleParam(param));
    }
    params = Collections.unmodifiableMap(builder);
  }

  @Override
  public RuleKey key() {
    return key;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String severity() {
    return severity;
  }

  @Override
  public RuleType type() {
    return type;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public String internalKey() {
    return internalKey;
  }

  @Override
  public RuleStatus status() {
    throw new UnsupportedOperationException("status");
  }

  @Override
  public DefaultStandaloneRuleParam param(String paramKey) {
    return params.get(paramKey);
  }

  @Override
  public Collection<RuleParam> params() {
    return params.values().stream().map(p -> (RuleParam) p).collect(toList());
  }

  @Override
  public Collection<StandaloneRuleParam> paramDetails() {
    return params.values().stream().map(p -> (StandaloneRuleParam) p).collect(toList());
  }

  @Override
  public boolean isActiveByDefault() {
    return isActiveByDefault;
  }

  @Override
  public String getKey() {
    return key.toString();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getHtmlDescription() {
    return description;
  }

  @Override
  public Language getLanguage() {
    return language;
  }

  @Override
  public String getSeverity() {
    return severity;
  }

  @Override
  public String getType() {
    return type.name();
  }

  @Override
  public String[] getTags() {
    return tags;
  }

  public Set<RuleKey> getDeprecatedKeys() {
    return deprecatedKeys;
  }
}
