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

import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Rules;
import org.sonar.api.profiles.ProfileDefinition;
import org.sonar.api.profiles.XMLProfileParser;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.global.ExtensionInstaller;
import org.sonarsource.sonarlint.core.container.global.ExtensionMatcher;
import org.sonarsource.sonarlint.core.container.global.ExtensionUtils;

public class StandaloneRuleRepositoryContainer extends ComponentContainer {

  private Rules rules;
  private ActiveRules activeRules;
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
    add(StandalonePluginRulesLoader.class,
      new StandaloneRulesProvider(),
      RuleFinderCompatibility.class,
      XMLProfileParser.class,
      new StandaloneActiveRulesProvider());
  }

  private void addPluginExtensions() {
    getComponentByType(ExtensionInstaller.class).install(this, new RuleAndProfileExtensionFilter());
  }

  static class RuleAndProfileExtensionFilter implements ExtensionMatcher {
    @Override
    public boolean accept(Object extension) {
      return ExtensionUtils.isType(extension, RulesDefinition.class)
        || ExtensionUtils.isType(extension, ProfileDefinition.class);
    }
  }

  @Override
  public void doAfterStart() {
    rules = getComponentByType(Rules.class);
    activeRules = getComponentByType(ActiveRules.class);
    StandalonePluginRulesLoader offlineRulesLoader = getComponentByType(StandalonePluginRulesLoader.class);
    ruleDefinitions = offlineRulesLoader.getContext();
  }

  public Rules getRules() {
    return rules;
  }

  public ActiveRules getActiveRules() {
    return activeRules;
  }

  public Context getRulesDefinitions() {
    return ruleDefinitions;
  }
}
