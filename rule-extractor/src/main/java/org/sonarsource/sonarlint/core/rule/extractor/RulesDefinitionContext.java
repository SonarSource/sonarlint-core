/*
 * SonarLint Core - Rule Extractor
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
package org.sonarsource.sonarlint.core.rule.extractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.internal.DefaultNewRepository;
import org.sonar.api.server.rule.internal.DefaultRepository;

public class RulesDefinitionContext extends RulesDefinition.Context {
  private final Map<String, RulesDefinition.Repository> repositoriesByKey = new HashMap<>();
  private String currentPluginKey = null;

  @Override
  public RulesDefinition.NewRepository createRepository(String key, String language) {
    return new DefaultNewRepository(this, key, language, false);
  }

  @Override
  public RulesDefinition.NewRepository createExternalRepository(String engineId, String language) {
    return new DefaultNewRepository(this, RuleKey.EXTERNAL_RULE_REPO_PREFIX + engineId, language, true);
  }

  @Override
  @CheckForNull
  public RulesDefinition.Repository repository(String key) {
    return repositoriesByKey.get(key);
  }

  @Override
  public List<RulesDefinition.Repository> repositories() {
    return List.copyOf(repositoriesByKey.values());
  }

  @Override
  public void registerRepository(DefaultNewRepository newRepository) {
    RulesDefinition.Repository existing = repositoriesByKey.get(newRepository.key());
    repositoriesByKey.put(newRepository.key(), new DefaultRepository(newRepository, existing));
  }

  @Override
  public String currentPluginKey() {
    return currentPluginKey;
  }

  @Override
  public void setCurrentPluginKey(@Nullable String pluginKey) {
    this.currentPluginKey = pluginKey;
  }
}
