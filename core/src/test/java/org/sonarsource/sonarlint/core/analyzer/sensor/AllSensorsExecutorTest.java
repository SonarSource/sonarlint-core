/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.events.SensorsPhaseHandler;
import org.sonar.api.batch.events.SensorsPhaseHandler.SensorsPhaseEvent;
import org.sonar.api.resources.Project;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AllSensorsExecutorTest {
  private AllSensorsExecutor executor;

  private Sensor sensor;
  private ScannerExtensionDictionnary dict;
  private Project project;
  private SensorsPhaseHandler handler;

  private SensorContext context;

  @Before
  public void setUp() {
    handler = mock(SensorsPhaseHandler.class);
    sensor = mock(Sensor.class);
    dict = mock(ScannerExtensionDictionnary.class);
    project = mock(Project.class);
    SensorsPhaseHandler[] handlers = {handler};
    context = mock(SensorContext.class);
    executor = new AllSensorsExecutor(context, dict, project, new ProgressWrapper(null), handlers);

    when(dict.select(Sensor.class, project, true)).thenReturn(Collections.singletonList(sensor));
  }

  @Test
  public void test() {
    executor.execute();

    InOrder inOrder = Mockito.inOrder(handler, sensor);

    inOrder.verify(handler).onSensorsPhase(ArgumentMatchers.argThat(new EventMatcher(true)));
    inOrder.verify(sensor).analyse(project, context);
    inOrder.verify(handler).onSensorsPhase(ArgumentMatchers.argThat(new EventMatcher(false)));
  }

  class EventMatcher implements ArgumentMatcher<SensorsPhaseEvent> {
    private boolean start;

    EventMatcher(boolean start) {
      this.start = start;
    }

    @Override
    public boolean matches(SensorsPhaseEvent argument) {
      return argument.isStart() == start && argument.getSensors().contains(sensor);
    }
  }
}
