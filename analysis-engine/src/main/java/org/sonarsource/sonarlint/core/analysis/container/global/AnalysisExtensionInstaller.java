/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.global;

import java.util.Set;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.analysis.container.ContainerLifespan;
import org.sonarsource.sonarlint.core.plugin.common.ExtensionInstaller;
import org.sonarsource.sonarlint.core.plugin.common.ExtensionUtils;
import org.sonarsource.sonarlint.core.plugin.common.Language;
import org.sonarsource.sonarlint.core.plugin.common.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.common.pico.ComponentContainer;
import org.sonarsource.sonarlint.plugin.api.SonarLintRuntime;

public class AnalysisExtensionInstaller extends ExtensionInstaller {

  private static final Logger LOG = Loggers.get(AnalysisExtensionInstaller.class);

  private final Set<Language> enabledLanguages;

  private final PluginInstancesRepository pluginInstancesRepository;

  public AnalysisExtensionInstaller(SonarLintRuntime sonarRuntime, PluginInstancesRepository pluginInstancesRepository, Configuration bootConfiguration,
    AnalysisEngineConfiguration globalConfig) {
    super(sonarRuntime, bootConfiguration);
    this.pluginInstancesRepository = pluginInstancesRepository;
    enabledLanguages = globalConfig.getEnabledLanguages();
  }

  public AnalysisExtensionInstaller install(ComponentContainer container, ContainerLifespan lifespan) {
    super.install(container, pluginInstancesRepository.getPluginInstancesByKeys(), (pluginKey, extension) -> {
      return lifespan.equals(getSonarLintSideLifespan(extension)) && onlySonarSourceSensor(pluginKey, extension);
    });
    return this;
  }

  private static ContainerLifespan getSonarLintSideLifespan(Object extension) {
    SonarLintSide annotation = AnnotationUtils.getAnnotation(extension, SonarLintSide.class);
    if (annotation != null) {
      String lifespan = annotation.lifespan();
      if (SonarLintSide.MULTIPLE_ANALYSES.equals(lifespan) || "INSTANCE".equals(lifespan)) {
        return ContainerLifespan.INSTANCE;
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
