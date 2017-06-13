/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.global;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.ExtensionProvider;
import org.sonar.api.Plugin;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.profiles.ProfileDefinition;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;
import org.sonarsource.sonarlint.core.plugin.PluginCacheLoader;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;

public class ExtensionInstaller {

  private static final Logger LOG = LoggerFactory.getLogger(ExtensionInstaller.class);

  private final SonarRuntime sqRuntime;
  private final PluginRepository pluginRepository;

  public ExtensionInstaller(SonarRuntime sqRuntime, PluginRepository pluginRepository) {
    this.sqRuntime = sqRuntime;
    this.pluginRepository = pluginRepository;
  }

  public ExtensionInstaller install(ComponentContainer container) {

    // plugin extensions
    for (PluginInfo pluginInfo : pluginRepository.getPluginInfos()) {
      Plugin plugin = pluginRepository.getPluginInstance(pluginInfo.getKey());
      Plugin.Context context = new Plugin.Context(sqRuntime);
      plugin.define(context);
      loadExtensions(container, pluginInfo, context);
    }
    List<ExtensionProvider> providers = container.getComponentsByType(ExtensionProvider.class);
    for (ExtensionProvider provider : providers) {
      Object object = provider.provide();
      if (object instanceof Iterable) {
        for (Object extension : (Iterable) object) {
          container.addExtension(null, extension);
        }
      } else {
        container.addExtension(null, object);
      }
    }
    return this;
  }

  private static void loadExtensions(ComponentContainer container, PluginInfo pluginInfo, Plugin.Context context) {
    for (Object extension : context.getExtensions()) {
      Boolean isSlPluginOrNull = pluginInfo.isSonarLintSupported();
      boolean isExplicitlySonarLintCompatible = isSlPluginOrNull != null && isSlPluginOrNull.booleanValue();
      if (isExplicitlySonarLintCompatible) {
        // When plugin itself claim to be compatible with SonarLint, only load @SonarLintSide extensions
        // filter out non officially supported Sensors
        if (ExtensionUtils.isSonarLintSide(extension) && (PluginCacheLoader.isWhitelisted(pluginInfo.getKey()) || isNotSensor(extension))) {
          container.addExtension(pluginInfo, extension);
        }
      } else if (!blacklisted(extension) && (ExtensionUtils.isScannerSide(extension) || ExtensionUtils.isType(extension, ProfileDefinition.class))) {
        // Here we have whitelisted extensions of whitelisted plugins
        container.addExtension(pluginInfo, extension);
      } else {
        LOG.debug("Extension {} was blacklisted as it is not used by SonarLint", className(extension));
      }
    }
  }

  private static boolean isNotSensor(Object extension) {
    return !ExtensionUtils.isType(extension, Sensor.class) && !ExtensionUtils.isType(extension, org.sonar.api.batch.Sensor.class);
  }

  private static boolean blacklisted(Object extension) {
    String className = className(extension);
    return className.contains("JaCoCo")
      || className.contains("Surefire")
      || className.contains("Coverage")
      || className.contains("COV")
      || className.contains("PhpUnit")
      || className.contains("XUnit")
      || className.contains("Pylint");
  }

  private static String className(Object extension) {
    return extension instanceof Class ? ((Class) extension).getName() : extension.getClass().getName();
  }

}
