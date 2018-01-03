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

import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

import static java.util.Arrays.asList;
import static org.sonarsource.sonarlint.core.analyzer.sensor.ScannerExtensionDictionnary.sort;

/**
 * Execute only new Sensors.
 */
public class NewSensorsExecutor implements SensorsExecutor {

  private static final Logger LOG = Loggers.get(NewSensorsExecutor.class);

  private final SensorOptimizer sensorOptimizer;
  private final ProgressWrapper progress;
  private final Sensor[] sensors;
  private final DefaultSensorContext context;

  public NewSensorsExecutor(DefaultSensorContext context, SensorOptimizer sensorOptimizer, ProgressWrapper progress) {
    this(context, sensorOptimizer, progress, new Sensor[0]);
  }

  public NewSensorsExecutor(DefaultSensorContext context, SensorOptimizer sensorOptimizer, ProgressWrapper progress, Sensor[] sensors) {
    this.context = context;
    this.sensors = sensors;
    this.sensorOptimizer = sensorOptimizer;
    this.progress = progress;
  }

  @Override
  public void execute() {
    for (Sensor sensor : sort(asList(sensors))) {
      progress.checkCancel();
      DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();
      sensor.describe(descriptor);
      if (sensorOptimizer.shouldExecute(descriptor)) {
        executeSensor(context, sensor, descriptor);
      }
    }
  }

  private static void executeSensor(SensorContext context, Sensor sensor, DefaultSensorDescriptor descriptor) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Execute Sensor: {}", descriptor.name() != null ? descriptor.name() : StringUtils.describe(sensor));
    }
    sensor.execute(context);
  }
}
