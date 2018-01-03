/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.analyzer.sensor;

import java.util.Collection;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.events.SensorsPhaseHandler;
import org.sonar.api.resources.Project;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

/**
 * Execute new and old Sensors.
 */
public class AllSensorsExecutor implements SensorsExecutor {

  private static final Logger LOG = Loggers.get(AllSensorsExecutor.class);

  private final Project module;
  private final ScannerExtensionDictionnary selector;
  private final SensorsPhaseHandler[] handlers;
  private final ProgressWrapper progress;
  private final SensorContext context;

  public AllSensorsExecutor(SensorContext context, ScannerExtensionDictionnary selector, Project project, ProgressWrapper progress) {
    this(context, selector, project, progress, new SensorsPhaseHandler[0]);
  }

  public AllSensorsExecutor(SensorContext context, ScannerExtensionDictionnary selector, Project project, ProgressWrapper progress, SensorsPhaseHandler[] handlers) {
    this.context = context;
    this.selector = selector;
    this.module = project;
    this.progress = progress;
    this.handlers = handlers;
  }

  @Override
  public void execute() {
    Collection<Sensor> sensors = selector.select(Sensor.class, module, true);

    for (SensorsPhaseHandler h : handlers) {
      h.onSensorsPhase(new DefaultSensorsPhaseEvent(sensors, true));
    }

    for (Sensor sensor : sensors) {
      progress.checkCancel();
      executeSensor(context, sensor);
    }

    for (SensorsPhaseHandler h : handlers) {
      h.onSensorsPhase(new DefaultSensorsPhaseEvent(sensors, false));
    }
  }

  private void executeSensor(SensorContext context, Sensor sensor) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Execute Sensor: {}", StringUtils.describe(sensor));
    }
    sensor.analyse(module, context);
  }
}
