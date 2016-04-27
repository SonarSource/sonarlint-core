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
package org.sonarsource.sonarlint.core.container.connected.rule;

import org.sonar.api.SonarPlugin;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonar.api.server.rule.RulesDefinitionXmlLoader;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.global.ExtensionInstaller;
import org.sonarsource.sonarlint.core.container.global.ExtensionMatcher;
import org.sonarsource.sonarlint.core.container.global.ExtensionUtils;
import org.sonarsource.sonarlint.core.container.standalone.rule.StandaloneRuleDefinitionsLoader;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginRepository;
import org.sonarsource.sonarlint.core.plugin.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.plugin.PluginCopier;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.PluginLoader;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;

public class StandaloneRuleDefinitionsContainer extends ComponentContainer {

  private final PluginReferences pluginReferences;
  private Context ruleDefinitions;

  public StandaloneRuleDefinitionsContainer(ComponentContainer parent, PluginReferences pluginReferences) {
    super(parent);
    this.pluginReferences = pluginReferences;
  }

  @Override
  protected void doBeforeStart() {
    addCoreComponents();
  }

  private void addCoreComponents() {
    add(StandaloneRuleDefinitionsLoader.class,
      RulesDefinitionXmlLoader.class,
      new TemporaryPluginIndexProvider(pluginReferences),
      DefaultPluginRepository.class,
      PluginCopier.class,
      PluginLoader.class,
      PluginClassloaderFactory.class,
      DefaultPluginJarExploder.class,
      ExtensionInstaller.class);
  }

  private void addPluginExtensions() {
    getComponentByType(ExtensionInstaller.class).install(this, new RuleDefinitionsExtensionFilter());
  }

  static class RuleDefinitionsExtensionFilter implements ExtensionMatcher {
    @Override
    public boolean accept(Object extension) {
      return ExtensionUtils.isType(extension, RulesDefinition.class);
    }
  }

  @Override
  public void doAfterStart() {
    installPlugins();
    addPluginExtensions();
    StandaloneRuleDefinitionsLoader offlineRulesLoader = getComponentByType(StandaloneRuleDefinitionsLoader.class);
    ruleDefinitions = offlineRulesLoader.getContext();
  }

  protected void installPlugins() {
    DefaultPluginRepository pluginRepository = getComponentByType(DefaultPluginRepository.class);
    for (PluginInfo pluginInfo : pluginRepository.getPluginInfos()) {
      SonarPlugin instance = pluginRepository.getPluginInstance(pluginInfo.getKey());
      addExtension(pluginInfo, instance);
    }
  }

  public Context getRulesDefinitions() {
    return ruleDefinitions;
  }
}
