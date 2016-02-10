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
package org.sonarsource.sonarlint.core.analyzer.sensor;

import org.sonar.api.batch.SensorContext;
import org.sonar.api.resources.Project;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintFileSystem;

public final class PhaseExecutor {

  private final SensorsExecutor sensorsExecutor;
  private final SensorContext sensorContext;
  private final SonarLintFileSystem fs;

  public PhaseExecutor(SensorsExecutor sensorsExecutor, SensorContext sensorContext, SonarLintFileSystem fs) {
    this.sensorsExecutor = sensorsExecutor;
    this.sensorContext = sensorContext;
    this.fs = fs;
  }

  /**
   * Executed on each module
   */
  public void execute(Project module) {

    fs.index();

    sensorsExecutor.execute(sensorContext);
  }

}
