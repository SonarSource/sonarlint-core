/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.sensor;

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.config.Configuration;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.analysis.sonarapi.DefaultSensorDescriptor;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

@SonarLintSide
public class SensorOptimizer {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final FileSystem fs;
  private final ActiveRules activeRules;
  private final Configuration config;

  public SensorOptimizer(FileSystem fs, ActiveRules activeRules, Configuration config) {
    this.fs = fs;
    this.activeRules = activeRules;
    this.config = config;
  }

  /**
   * Decide if the given Sensor should be executed.
   */
  public boolean shouldExecute(DefaultSensorDescriptor descriptor) {
    if (!fsCondition(descriptor)) {
      LOG.debug("'{}' skipped because there is no related files in the current project", descriptor.name());
      return false;
    }
    if (!activeRulesCondition(descriptor)) {
      LOG.debug("'{}' skipped because there is no related rules activated", descriptor.name());
      return false;
    }
    if (!settingsCondition(descriptor)) {
      LOG.debug("'{}' skipped because one of the required properties is missing", descriptor.name());
      return false;
    }
    return true;
  }

  private boolean settingsCondition(DefaultSensorDescriptor descriptor) {
    if (descriptor.configurationPredicate() != null) {
      return descriptor.configurationPredicate().test(config);
    }
    return true;
  }

  private boolean activeRulesCondition(DefaultSensorDescriptor descriptor) {
    if (!descriptor.ruleRepositories().isEmpty()) {
      for (String repoKey : descriptor.ruleRepositories()) {
        if (!activeRules.findByRepository(repoKey).isEmpty()) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  private boolean fsCondition(DefaultSensorDescriptor descriptor) {
    if (!descriptor.languages().isEmpty() || descriptor.type() != null) {
      var langPredicate = descriptor.languages().isEmpty() ? fs.predicates().all() : fs.predicates().hasLanguages(descriptor.languages());

      var typePredicate = descriptor.type() == null ? fs.predicates().all() : fs.predicates().hasType(descriptor.type());
      return fs.hasFiles(fs.predicates().and(langPredicate, typePredicate));
    }
    return true;
  }

}
