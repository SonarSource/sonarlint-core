/*
 * SonarLint Core - Medium Tests
 * Copyright (C) SonarSource Sàrl
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
package mediumtest.plugin.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.scanner.ResourceFetcher;

/**
 * Reads {@code fetch <pluginKey> <path>} or {@code extract <pluginKey> <path>} from the analyzed file
 * and writes the resource text, or the failure, next to it.
 */
public class StaticResourceSensor implements Sensor {

  private final ResourceFetcher resources;

  public StaticResourceSensor(ResourceFetcher resources) {
    this.resources = resources;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("Static resources");
  }

  @Override
  public void execute(SensorContext context) {
    for (var file : context.fileSystem().inputFiles(context.fileSystem().predicates().all())) {
      var report = Path.of(file.uri()).resolveSibling(file.filename() + ".result");
      try {
        Files.writeString(report, run(file));
      } catch (RuntimeException | IOException e) {
        writeFailure(report, e);
      }
    }
  }

  private String run(InputFile file) throws IOException {
    var parts = file.contents().trim().split(" ", 3);
    var pluginKey = parts[1];
    var path = parts[2];
    if ("extract".equals(parts[0])) {
      return Files.readString(resources.fetchAndExtract(pluginKey, path).resolve("hello.txt"));
    }
    return Files.readString(resources.fetch(pluginKey, path));
  }

  private static void writeFailure(Path report, Exception e) {
    try {
      Files.writeString(report, e.getClass().getSimpleName() + ": " + e.getMessage());
    } catch (IOException io) {
      throw new IllegalStateException(io);
    }
  }
}
