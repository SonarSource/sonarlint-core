/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.scanner.sensor.ProjectSensor;
import org.sonarsource.sonarlint.core.analysis.sonarapi.DefaultSensorContext;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressIndicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensorsExecutorTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  public static final DefaultSensorContext DEFAULT_SENSOR_CONTEXT = new DefaultSensorContext(null, null, null, null, null, null, null, new ProgressIndicator() {
    @Override
    public void notifyProgress(@Nullable String message, @Nullable Integer percentage) {
      // no-op
    }

    @Override
    public boolean isCanceled() {
      return false;
    }
  });

  private static class MyClass {
    @Override
    public String toString() {
      return null;
    }
  }

  @Test
  void testDescribe() {
    Object withToString = new Object() {
      @Override
      public String toString() {
        return "desc";
      }
    };

    var withoutToString = new Object();

    assertThat(SensorsExecutor.describe(withToString)).isEqualTo(("desc"));
    assertThat(SensorsExecutor.describe(withoutToString)).isEqualTo("java.lang.Object");
    assertThat(SensorsExecutor.describe(new MyClass())).endsWith("MyClass");
  }

  @Test
  void testThrowingSensorShouldBeLogged() {
    var sensorOptimizer = mock(SensorOptimizer.class);
    when(sensorOptimizer.shouldExecute(any())).thenReturn(true);
    var executor = new SensorsExecutor(DEFAULT_SENSOR_CONTEXT, sensorOptimizer, Optional.empty(), Optional.of(List.of(new ThrowingSensor())));

    executor.execute();

    assertThat(logTester.logs(LogOutput.Level.ERROR)).contains("Error executing sensor: 'Throwing sensor'");
  }

  @Test
  void shouldRunGlobalSensorLast() {
    var sensorOptimizer = mock(SensorOptimizer.class);
    when(sensorOptimizer.shouldExecute(any())).thenReturn(true);

    var regularSensor = new RegularSensor();
    var globalSensor = new GlobalSensor();
    var oldGlobalSensor = new OldGlobalSensor();

    var executor = new SensorsExecutor(DEFAULT_SENSOR_CONTEXT, sensorOptimizer, Optional.empty(), Optional.of(List.of(globalSensor, regularSensor, oldGlobalSensor)));

    executor.execute();

    assertThat(logTester.logs(LogOutput.Level.INFO)).containsExactly("Executing 'Regular sensor'", "Executing 'Global sensor'", "Executing 'Old Global sensor'");
  }

  private static class ThrowingSensor implements Sensor {
    @Override
    public void describe(SensorDescriptor descriptor) {
      descriptor.name("Throwing sensor");
    }

    @Override
    public void execute(SensorContext context) {
      throw new Error();
    }
  }

  private static class RegularSensor implements Sensor {
    @Override
    public void describe(SensorDescriptor descriptor) {
      descriptor.name("Regular sensor");
    }

    @Override
    public void execute(SensorContext context) {
      SonarLintLogger.get().info("Executing 'Regular sensor'");
    }
  }

  private static class GlobalSensor implements ProjectSensor {

    @Override
    public void describe(SensorDescriptor descriptor) {
      descriptor.name("Global sensor");
    }

    @Override
    public void execute(SensorContext context) {
      SonarLintLogger.get().info("Executing 'Global sensor'");
    }
  }

  private static class OldGlobalSensor implements Sensor {
    @Override
    public void describe(SensorDescriptor descriptor) {
      descriptor.name("Old Global sensor").global();
    }

    @Override
    public void execute(SensorContext context) {
      SonarLintLogger.get().info("Executing 'Old Global sensor'");
    }
  }


}
