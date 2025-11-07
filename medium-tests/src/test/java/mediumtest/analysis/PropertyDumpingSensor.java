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
package mediumtest.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;

public class PropertyDumpingSensor implements Sensor {

  public static final String PROPERTY_NAME_TO_DUMP = "PROPERTY_NAME_TO_CHECK";

  @Override
  public void describe(SensorDescriptor sensorDescriptor) {
    sensorDescriptor.name("Configuration dumping sensor");
  }

  @Override
  public void execute(SensorContext sensorContext) {
    var propertyName = sensorContext.config().get(PROPERTY_NAME_TO_DUMP).orElseThrow();
    sensorContext.config().get(propertyName).ifPresent(value -> dump(value, sensorContext.fileSystem().baseDir()));
  }

  private void dump(String propertyValue, File baseDir) {
    try {
      Files.writeString(baseDir.toPath().resolve("property.dump"), propertyValue);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
