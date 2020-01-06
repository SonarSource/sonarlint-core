/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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
import javax.annotation.CheckForNull;
import javax.annotation.concurrent.Immutable;
import org.sonar.api.batch.rule.RuleParam;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Param;
import org.sonar.markdown.Markdown;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintRule;

import static java.util.stream.Collectors.toList;

@Immutable
public class StandaloneRule implements SonarLintRule, RuleDetails {

  private final RuleKey key;
  private final String name;
  private final String severity;
  private final RuleType type;
  private final String description;
  private final String internalKey;
  private final Map<String, StandaloneRuleParam> params;
  private boolean isActiveByDefault;
  private final String languageKey;
  private final String[] tags;
  private Set<RuleKey> deprecatedKeys;

  public StandaloneRule(RulesDefinition.Rule rule) {
    this.key = RuleKey.of(rule.repository().key(), rule.key());
    this.name = rule.name();
    this.severity = rule.severity();
    this.type = rule.type();
    this.description = rule.htmlDescription() != null ? rule.htmlDescription() : Markdown.convertToHtml(rule.markdownDescription());
    this.internalKey = rule.internalKey();
    this.isActiveByDefault = rule.activatedByDefault();
    this.languageKey = rule.repository().language();
    this.tags = rule.tags().toArray(new String[0]);
    this.deprecatedKeys = rule.deprecatedRuleKeys();

    Map<String, StandaloneRuleParam> builder = new HashMap<>();
    for (Param param : rule.params()) {
      builder.put(param.key(), new StandaloneRuleParam(param));
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
  @CheckForNull
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
  public StandaloneRuleParam param(String paramKey) {
    return params.get(paramKey);
  }

  @Override
  public Collection<RuleParam> params() {
    return params.values().stream().map(r -> (RuleParam) r).collect(toList());
  }

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
  public String getLanguageKey() {
    return languageKey;
  }

  @Override
  public String getSeverity() {
    return severity;
  }

  @Override
  public String getType() {
    return type != null ? type.name() : null;
  }

  @Override
  public String[] getTags() {
    return tags;
  }

  @Override
  public String getExtendedDescription() {
    return "";
  }

  public Set<RuleKey> getDeprecatedKeys() {
    return deprecatedKeys;
  }
}
