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
package org.sonarsource.sonarlint.core.container.global;

import org.sonar.api.SonarPlugin;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.analysis.IssueListener;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.standalone.StandaloneGlobalContainer;
import org.sonarsource.sonarlint.core.container.storage.StorageGlobalContainer;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginRepository;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;

public abstract class GlobalContainer extends ComponentContainer {

  public static GlobalContainer create(GlobalConfiguration globalConfig) {
    return globalConfig.getServerId() != null ? StorageGlobalContainer.create(globalConfig) : StandaloneGlobalContainer.create(globalConfig);
  }

  protected void installPlugins() {
    DefaultPluginRepository pluginRepository = getComponentByType(DefaultPluginRepository.class);
    for (PluginInfo pluginInfo : pluginRepository.getPluginInfos()) {
      SonarPlugin instance = pluginRepository.getPluginInstance(pluginInfo.getKey());
      addExtension(pluginInfo, instance);
    }
  }

  public abstract AnalysisResults analyze(AnalysisConfiguration configuration, IssueListener issueListener);

  public abstract RuleDetails getRuleDetails(String ruleKeyStr);

}
