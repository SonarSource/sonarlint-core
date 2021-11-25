/*
 * SonarLint Core - Plugin Common
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
package org.sonarsource.sonarlint.core.plugin.common;

import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import org.sonar.api.Plugin;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.plugin.common.load.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.common.pico.ComponentContainer;
import org.sonarsource.sonarlint.plugin.api.SonarLintRuntime;

public class ExtensionInstaller {

  private static final Logger LOG = Loggers.get(ExtensionInstaller.class);

  private final SonarLintRuntime sonarRuntime;
  private final PluginInstancesRepository pluginInstancesRepository;
  private final Configuration bootConfiguration;
  private final Set<Language> enabledLanguages;

  public ExtensionInstaller(SonarLintRuntime sonarRuntime, PluginInstancesRepository pluginInstancesRepository, Configuration bootConfiguration,
    Set<Language> enabledLanguages) {
    this.sonarRuntime = sonarRuntime;
    this.pluginInstancesRepository = pluginInstancesRepository;
    this.bootConfiguration = bootConfiguration;
    this.enabledLanguages = enabledLanguages;
  }

  public ExtensionInstaller install(ComponentContainer container, Predicate<Object> extensionFilter) {
    return install(container, pluginInstancesRepository.getActivePluginInfos(), extensionFilter);
  }

  private ExtensionInstaller install(ComponentContainer container, Collection<PluginInfo> pluginInfos, Predicate<Object> extensionFilter) {
    for (PluginInfo pluginInfo : pluginInfos) {
      Plugin plugin = pluginInstancesRepository.getPluginInstance(pluginInfo.getKey());
      Plugin.Context context = new PluginContextImpl.Builder()
        .setSonarRuntime(sonarRuntime)
        .setBootConfiguration(bootConfiguration)
        .build();
      plugin.define(context);
      loadExtensions(container, pluginInfo.getKey(), context, extensionFilter);
    }
    return this;
  }

  private void loadExtensions(ComponentContainer container, String pluginKey, Plugin.Context context, Predicate<Object> extensionFilter) {
    for (Object extension : context.getExtensions()) {
      // filter out non officially supported Sensors
      if (extensionFilter.test(extension) && onlySonarSourceSensor(pluginKey, extension)) {
        container.addExtension(pluginKey, extension);
      }
    }
  }

  private boolean onlySonarSourceSensor(String pluginKey, Object extension) {
    // SLCORE-259
    if (!enabledLanguages.contains(Language.TS) && className(extension).contains("TypeScriptSensor")) {
      LOG.debug("TypeScript sensor excluded");
      return false;
    }
    return Language.containsPlugin(pluginKey) || isNotSensor(extension);
  }

  private static boolean isNotSensor(Object extension) {
    return !ExtensionUtils.isType(extension, Sensor.class);
  }

  private static String className(Object extension) {
    return extension instanceof Class ? ((Class) extension).getName() : extension.getClass().getName();
  }

}
