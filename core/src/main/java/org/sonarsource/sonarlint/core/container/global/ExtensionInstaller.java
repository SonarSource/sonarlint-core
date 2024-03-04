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
package org.sonarsource.sonarlint.core.container.global;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.Plugin;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.ContainerLifespan;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;
import org.sonarsource.sonarlint.plugin.api.SonarLintRuntime;

public class ExtensionInstaller {

  private static final Logger LOG = Loggers.get(ExtensionInstaller.class);

  private final SonarLintRuntime sonarRuntime;
  private final PluginRepository pluginRepository;
  private final Configuration bootConfiguration;
  private final PluginVersionChecker pluginVersionChecker;
  private final Set<Language> enabledLanguages;

  public ExtensionInstaller(SonarLintRuntime sonarRuntime, PluginRepository pluginRepository, Configuration bootConfiguration, PluginVersionChecker pluginVersionChecker,
    AbstractGlobalConfiguration globalConfig) {
    this.sonarRuntime = sonarRuntime;
    this.pluginRepository = pluginRepository;
    this.bootConfiguration = bootConfiguration;
    this.pluginVersionChecker = pluginVersionChecker;
    this.enabledLanguages = globalConfig.getEnabledLanguages();
  }

  public void installEmbeddedOnly(ComponentContainer container, ContainerLifespan lifespan) {
    Collection<PluginInfo> pluginInfos = pluginRepository.getActivePluginInfos().stream().filter(PluginInfo::isEmbedded).collect(Collectors.toList());
    install(container, lifespan, pluginInfos);
  }

  public ExtensionInstaller install(ComponentContainer container, ContainerLifespan lifespan) {
    return install(container, lifespan, pluginRepository.getActivePluginInfos());
  }

  private ExtensionInstaller install(ComponentContainer container, ContainerLifespan lifespan, Collection<PluginInfo> pluginInfos) {
    for (PluginInfo pluginInfo : pluginInfos) {
      Plugin plugin = pluginRepository.getPluginInstance(pluginInfo.getKey());
      Plugin.Context context = new PluginContextImpl.Builder()
        .setSonarRuntime(sonarRuntime)
        .setBootConfiguration(bootConfiguration)
        .build();
      plugin.define(context);
      loadExtensions(container, pluginInfo, context, lifespan);
    }
    return this;
  }

  private void loadExtensions(ComponentContainer container, PluginInfo pluginInfo, Plugin.Context context, ContainerLifespan lifespan) {
    Boolean isSlPluginOrNull = pluginInfo.isSonarLintSupported();
    boolean isExplicitlySonarLintCompatible = isSlPluginOrNull != null && isSlPluginOrNull;
    if (lifespan.equals(ContainerLifespan.ENGINE) && !isExplicitlySonarLintCompatible) {
      // Don't support global extensions for old plugins
      return;
    }
    for (Object extension : context.getExtensions()) {
      if (isExplicitlySonarLintCompatible) {
        // When plugin itself claim to be compatible with SonarLint, only load @SonarLintSide extensions
        // filter out non officially supported Sensors
        if (lifespan.equals(getSonarLintSideLifespan(extension)) && onlySonarSourceSensor(pluginInfo, extension)) {
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

  private static ContainerLifespan getSonarLintSideLifespan(Object extension) {
    SonarLintSide sonarPluginLegacyAnnotation = AnnotationUtils.getAnnotation(extension, SonarLintSide.class);
    if (sonarPluginLegacyAnnotation != null) {
      String lifespan = sonarPluginLegacyAnnotation.lifespan();
      if (SonarLintSide.MULTIPLE_ANALYSES.equals(lifespan) || "ENGINE".equals(lifespan)) {
        return ContainerLifespan.ENGINE;
      }
      if ("MODULE".equals(lifespan)) {
        return ContainerLifespan.MODULE;
      }
      if (SonarLintSide.SINGLE_ANALYSIS.equals(lifespan)) {
        return ContainerLifespan.ANALYSIS;
      }
    }
    return null;
  }

  private static boolean isNotSensor(Object extension) {
    return !ExtensionUtils.isType(extension, Sensor.class);
  }

  private static String className(Object extension) {
    return extension instanceof Class ? ((Class) extension).getName() : extension.getClass().getName();
  }

}
