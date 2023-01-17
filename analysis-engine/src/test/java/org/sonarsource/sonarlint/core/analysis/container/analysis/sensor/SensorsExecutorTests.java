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

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensorsExecutorTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

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
    var executor = new SensorsExecutor(null, sensorOptimizer, new ProgressMonitor(null), Optional.of(List.of(new ThrowingSensor())));

    executor.execute();

    assertThat(logTester.logs(ClientLogOutput.Level.ERROR)).contains("Error executing sensor: 'Throwing sensor'");
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

}
