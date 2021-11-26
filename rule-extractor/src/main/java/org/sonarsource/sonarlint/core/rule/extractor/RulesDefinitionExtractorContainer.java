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

import org.sonar.api.Startable;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonar.api.utils.Version;
import org.sonarsource.sonarlint.core.plugin.common.ApiVersions;
import org.sonarsource.sonarlint.core.plugin.common.ExtensionInstaller;
import org.sonarsource.sonarlint.core.plugin.common.ExtensionUtils;
import org.sonarsource.sonarlint.core.plugin.common.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.common.pico.ComponentContainer;
import org.sonarsource.sonarlint.core.plugin.common.sonarapi.SonarLintRuntimeImpl;
import org.sonarsource.sonarlint.plugin.api.SonarLintRuntime;

public class RulesDefinitionExtractorContainer extends ComponentContainer {

  private final PluginInstancesRepository pluginInstancesRepository;
  private Context context;

  public RulesDefinitionExtractorContainer(PluginInstancesRepository pluginInstancesRepository) {
    this.pluginInstancesRepository = pluginInstancesRepository;
  }

  @Override
  protected void doBeforeStart() {
    Version sonarPluginApiVersion = ApiVersions.loadSonarPluginApiVersion();
    Version sonarlintPluginApiVersion = ApiVersions.loadSonarLintPluginApiVersion();

    SonarLintRuntime sonarLintRuntime = new SonarLintRuntimeImpl(sonarPluginApiVersion, sonarlintPluginApiVersion, -1);

    EmptyConfiguration config = new EmptyConfiguration();

    ExtensionInstaller extensionInstaller = new ExtensionInstaller(sonarLintRuntime, config);
    extensionInstaller.install(this, pluginInstancesRepository.getPluginInstancesByKeys(), (key, ext) -> {
      return !ExtensionUtils.isType(ext, Startable.class);
    });

    add(
      config,
      sonarLintRuntime,
      RuleDefinitionsLoader.class,
      NoopTempFolder.class,
      EmptySettings.class);
  }

  @Override
  protected void doAfterStart() {
    this.context = getComponentByType(RuleDefinitionsLoader.class).getContext();
  }

  public Context getContext() {
    return context;
  }

}
