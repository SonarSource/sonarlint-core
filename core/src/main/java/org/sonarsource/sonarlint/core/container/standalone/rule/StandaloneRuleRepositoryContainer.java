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

import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonarsource.sonarlint.core.container.ContainerLifespan;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintRules;
import org.sonarsource.sonarlint.core.container.global.ExtensionInstaller;
import org.sonarsource.sonarlint.core.plugin.commons.pico.ComponentContainer;

import static java.util.stream.Collectors.toList;

public class StandaloneRuleRepositoryContainer extends ComponentContainer {

  private SonarLintRules rules;
  private Context ruleDefinitions;

  public StandaloneRuleRepositoryContainer(ComponentContainer parent) {
    super(parent);
  }

  @Override
  protected void doBeforeStart() {
    addPluginExtensions();
    addCoreComponents();
  }

  private void addCoreComponents() {
    add(StandaloneRuleDefinitionsLoader.class,
      new StandaloneSonarLintRulesProvider(),
      new EmptyConfiguration());
  }

  private void addPluginExtensions() {
    getComponentByType(ExtensionInstaller.class).installEmbeddedOnly(this, ContainerLifespan.ANALYSIS);
  }

  @Override
  public void doAfterStart() {
    rules = getComponentByType(SonarLintRules.class);
    StandaloneRuleDefinitionsLoader offlineRulesLoader = getComponentByType(StandaloneRuleDefinitionsLoader.class);
    ruleDefinitions = offlineRulesLoader.getContext();
  }

  public SonarLintRules getRules() {
    return rules;
  }

  public StandaloneActiveRules getStandaloneActiveRules() {
    return new StandaloneActiveRules(rules.findAll().stream().map(StandaloneRule.class::cast).collect(toList()));
  }

  public Context getRulesDefinitions() {
    return ruleDefinitions;
  }
}
