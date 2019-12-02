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
package org.sonarsource.sonarlint.core.container.global;

import java.util.Collections;
import java.util.Set;
import org.sonar.api.Plugin;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.config.Configuration;
import org.sonar.api.internal.PluginContextImpl;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.Language;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;

public class ExtensionInstaller {

  private static final Logger LOG = Loggers.get(ExtensionInstaller.class);

  private final SonarRuntime sonarRuntime;
  private final PluginRepository pluginRepository;
  private final Configuration bootConfiguration;
  private final PluginVersionChecker pluginVersionChecker;
  private final Set<Language> enabledLanguages;

  /**
   * Standalone mode
   */
  public ExtensionInstaller(SonarRuntime sonarRuntime, PluginRepository pluginRepository, Configuration bootConfiguration, PluginVersionChecker pluginVersionChecker) {
    this(sonarRuntime, pluginRepository, bootConfiguration, pluginVersionChecker, Collections.emptySet());
  }

  /**
   * Connected mode
   */
  public ExtensionInstaller(SonarRuntime sonarRuntime, PluginRepository pluginRepository, Configuration bootConfiguration, PluginVersionChecker pluginVersionChecker,
    ConnectedGlobalConfiguration connectedGlobalConfig) {
    this(sonarRuntime, pluginRepository, bootConfiguration, pluginVersionChecker, connectedGlobalConfig.getEnabledLanguages());
  }

  private ExtensionInstaller(SonarRuntime sonarRuntime, PluginRepository pluginRepository, Configuration bootConfiguration, PluginVersionChecker pluginVersionChecker,
    Set<Language> enabledLanguages) {
    this.sonarRuntime = sonarRuntime;
    this.pluginRepository = pluginRepository;
    this.bootConfiguration = bootConfiguration;
    this.pluginVersionChecker = pluginVersionChecker;
    this.enabledLanguages = enabledLanguages;
  }

  public ExtensionInstaller install(ComponentContainer container, boolean global) {

    // plugin extensions
    for (PluginInfo pluginInfo : pluginRepository.getPluginInfos()) {
      Plugin plugin = pluginRepository.getPluginInstance(pluginInfo.getKey());
      Plugin.Context context = new PluginContextImpl.Builder()
        .setSonarRuntime(sonarRuntime)
        .setBootConfiguration(bootConfiguration)
        .build();
      plugin.define(context);
      loadExtensions(container, pluginInfo, context, global);
    }
    return this;
  }

  private void loadExtensions(ComponentContainer container, PluginInfo pluginInfo, Plugin.Context context, boolean global) {
    Boolean isSlPluginOrNull = pluginInfo.isSonarLintSupported();
    boolean isExplicitlySonarLintCompatible = isSlPluginOrNull != null && isSlPluginOrNull.booleanValue();
    if (global && !isExplicitlySonarLintCompatible) {
      // Don't support global extensions for old plugins
      return;
    }
    for (Object extension : context.getExtensions()) {
      if (isExplicitlySonarLintCompatible) {
        // When plugin itself claim to be compatible with SonarLint, only load @SonarLintSide extensions
        // filter out non officially supported Sensors
        if (isSonarLintSide(extension) && (isGlobal(extension) == global) && onlySonarSourceSensor(pluginInfo, extension)) {
          container.addExtension(pluginInfo, extension);
        }
      } else {
        LOG.debug("Extension {} was blacklisted as it is not used by SonarLint", className(extension));
      }
    }
  }

  private boolean onlySonarSourceSensor(PluginInfo pluginInfo, Object extension) {
    // SLCORE-259
    if (!enabledLanguages.contains(Language.TS) && className(extension).contains("TypeScriptSensor")) {
      LOG.debug("TypeScript sensor excluded");
      return false;
    }
    return pluginVersionChecker.getMinimumVersion(pluginInfo.getKey()) != null || isNotSensor(extension);
  }

  private static boolean isSonarLintSide(Object extension) {
    return ExtensionUtils.isSonarLintSide(extension);
  }

  /**
   * Experimental. Used by SonarTS
   */
  private static boolean isGlobal(Object extension) {
    SonarLintSide annotation = AnnotationUtils.getAnnotation(extension, SonarLintSide.class);
    return annotation != null && SonarLintSide.MULTIPLE_ANALYSES.equals(annotation.lifespan());
  }

  private static boolean isNotSensor(Object extension) {
    return !ExtensionUtils.isType(extension, Sensor.class);
  }

  private static String className(Object extension) {
    return extension instanceof Class ? ((Class) extension).getName() : extension.getClass().getName();
  }

}
