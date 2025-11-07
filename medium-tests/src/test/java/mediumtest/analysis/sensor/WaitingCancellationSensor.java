/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package mediumtest.analysis.sensor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;

public class WaitingCancellationSensor implements Sensor {

  public static final String CANCELLATION_FILE_PATH_PROPERTY_NAME = "cancellation.file.path";

  @Override
  public void describe(SensorDescriptor sensorDescriptor) {
    sensorDescriptor.name("WaitingCancellationSensor");
  }

  @Override
  public void execute(SensorContext sensorContext) {
    var cancellationFilePath = Path.of(sensorContext.config().get(CANCELLATION_FILE_PATH_PROPERTY_NAME)
      .orElseThrow(() -> new IllegalArgumentException("Missing '" + CANCELLATION_FILE_PATH_PROPERTY_NAME + "' property")));
    var startTime = System.currentTimeMillis();
    while (!sensorContext.isCancelled() && startTime + 8000 > System.currentTimeMillis()) {
      System.out.println("Helloooo");
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    System.out.println("Context cancelled: " + sensorContext.isCancelled());
    if (sensorContext.isCancelled()) {
      try {
        Files.writeString(cancellationFilePath, "CANCELED");
        System.out.println("Wrote to cancellation file: " + cancellationFilePath);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
