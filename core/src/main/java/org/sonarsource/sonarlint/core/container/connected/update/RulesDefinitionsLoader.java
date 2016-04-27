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
package org.sonarsource.sonarlint.core.container.connected.update;

import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.connected.rule.StandaloneRuleDefinitionsContainer;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;

public class RulesDefinitionsLoader {

  private final ComponentContainer container;

  public RulesDefinitionsLoader(ComponentContainer container) {
    this.container = container;
  }

  public Context loadRuleDefinitions(PluginReferences pluginReferences) {
    StandaloneRuleDefinitionsContainer ruleDefinitionsContainer = new StandaloneRuleDefinitionsContainer(container, pluginReferences);
    ruleDefinitionsContainer.execute();
    return ruleDefinitionsContainer.getRulesDefinitions();
  }

}
