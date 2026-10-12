/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.sensor;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.scanner.sensor.ProjectSensor;
import org.sonarsource.sonarlint.core.analysis.container.analysis.AnalysisContainer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileIndexer;
import org.sonarsource.sonarlint.core.analysis.sonarapi.DefaultSensorContext;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressIndicator;
import org.sonarsource.sonarlint.core.commons.tracing.Trace;
import org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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

  private final SpringComponentContainer container = new SpringComponentContainer();
  private final SensorOptimizer sensorOptimizer = mock(SensorOptimizer.class);

  @BeforeEach
  void setUp() {
    when(sensorOptimizer.shouldExecute(any())).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    container.stopComponents();
  }

  private SensorsExecutor createExecutor(Object... components) {
    container.add(components);
    container.startComponents();
    return new SensorsExecutor(DEFAULT_SENSOR_CONTEXT, sensorOptimizer, null, container);
  }

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
    var executor = createExecutor(new ThrowingSensor());

    executor.execute();

    assertThat(logTester.logs(LogOutput.Level.ERROR)).contains("Error executing sensor: 'Throwing sensor'");
  }

  @Test
  void shouldRunGlobalSensorLast() {
    var regularSensor = new RegularSensor();
    var globalSensor = new GlobalSensor();
    var oldGlobalSensor = new OldGlobalSensor();

    var executor = createExecutor(globalSensor, regularSensor, oldGlobalSensor);

    executor.execute();

    assertThat(logTester.logs(LogOutput.Level.INFO)).containsExactly("Executing 'Regular sensor'", "Executing 'Global sensor'", "Executing 'Old Global sensor'");
  }

  @Test
  void should_skip_sensor_creation_failures_and_execute_healthy_sensors() {
    var executor = createExecutor(RegularSensor.class, ConstructorFailure.class, ConstructorError.class, DependencyFailure.class, GlobalSensor.class, FailingDependency.class);

    executor.execute();

    assertThat(logTester.logs(LogOutput.Level.INFO)).containsExactly("Executing 'Regular sensor'", "Executing 'Global sensor'");
    assertThat(logTester.logs(LogOutput.Level.ERROR)).contains(
      creationError(ConstructorFailure.class), creationError(ConstructorError.class), creationError(DependencyFailure.class));
    assertThat(logTester.logs(LogOutput.Level.ERROR)).anySatisfy(log -> assertThat(log).contains("constructor failure"));
    assertThat(logTester.logs(LogOutput.Level.ERROR)).anySatisfy(log -> assertThat(log).contains("java.lang.LinkageError: constructor error"));
    assertThat(logTester.logs(LogOutput.Level.ERROR)).anySatisfy(log -> assertThat(log).contains("dependency failure"));
  }

  @Test
  void should_execute_with_no_sensors() {
    createExecutor().execute();

    assertThat(logTester.logs(LogOutput.Level.ERROR)).isEmpty();
    assertThat(logTester.logs(LogOutput.Level.INFO)).isEmpty();
  }

  @Test
  void should_execute_when_all_sensor_creations_fail() {
    createExecutor(ConstructorFailure.class).execute();

    assertThat(logTester.logs(LogOutput.Level.ERROR)).contains(creationError(ConstructorFailure.class));
    assertThat(logTester.logs(LogOutput.Level.INFO)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void should_create_sensors_after_indexing_using_the_current_analysis_container(boolean withTrace) {
    var state = new IndexingState();
    var indexer = mock(FileIndexer.class);
    doAnswer(invocation -> {
      state.events.add("indexed");
      return null;
    }).when(indexer).index();
    container.add(DEFAULT_SENSOR_CONTEXT, sensorOptimizer, indexer, state);
    var trace = withTrace ? Trace.begin("analysis", "test") : null;
    if (trace != null) {
      container.add(trace);
    }
    container.startComponents();
    var analysisContainer = new AnalysisContainer(container, mock(ProgressIndicator.class)) {
      @Override
      protected void doBeforeStart() {
        add(SensorsExecutor.class, RequiresIndexedSensor.class, ConstructorFailure.class);
      }
    };

    analysisContainer.startComponents();

    assertThat(state.sensorContainer).isSameAs(analysisContainer);
    assertThat(state.events).containsExactly("indexed", "created", "executed");
    assertThat(logTester.logs(LogOutput.Level.ERROR)).contains(creationError(ConstructorFailure.class));
    if (trace != null) {
      trace.finishSuccessfully();
    }
  }

  @Test
  void should_preserve_cancellation() {
    var context = mock(DefaultSensorContext.class);
    when(context.isCancelled()).thenReturn(true);
    container.add(new RegularSensor());
    container.startComponents();

    new SensorsExecutor(context, sensorOptimizer, null, container).execute();

    assertThat(logTester.logs(LogOutput.Level.INFO)).isEmpty();
    assertThat(logTester.logs(LogOutput.Level.DEBUG)).contains("Analysis is canceled");
  }

  @Test
  void should_propagate_describe_failures() {
    var executor = createExecutor(new DescribeFailure());

    assertThatThrownBy(executor::execute).isInstanceOf(IllegalStateException.class).hasMessage("describe failure");
    assertThat(logTester.logs(LogOutput.Level.ERROR)).isEmpty();
  }

  private static String creationError(Class<?> sensorClass) {
    return "Error creating sensor: '" + sensorClass.getClassLoader() + "-" + sensorClass.getCanonicalName() + "'";
  }

  private static class ConstructorFailure extends RegularSensor {
    public ConstructorFailure() {
      throw new IllegalStateException("constructor failure");
    }
  }

  private static class ConstructorError extends RegularSensor {
    public ConstructorError() {
      throw new LinkageError("constructor error");
    }
  }

  private static class FailingDependency {
    public FailingDependency() {
      throw new IllegalStateException("dependency failure");
    }
  }

  private static class DependencyFailure extends RegularSensor {
    public DependencyFailure(FailingDependency dependency) {
      // The dependency fails before this constructor is invoked.
    }
  }

  private static class DescribeFailure extends RegularSensor {
    @Override
    public void describe(SensorDescriptor descriptor) {
      throw new IllegalStateException("describe failure");
    }
  }

  private static class IndexingState {
    private final List<String> events = new ArrayList<>();
    private SpringComponentContainer sensorContainer;
  }

  private static class RequiresIndexedSensor implements ProjectSensor {
    private final IndexingState state;

    public RequiresIndexedSensor(IndexingState state, SpringComponentContainer container) {
      assertThat(state.events).containsExactly("indexed");
      this.state = state;
      state.sensorContainer = container;
      state.events.add("created");
    }

    @Override
    public void describe(SensorDescriptor descriptor) {
      descriptor.name("Indexed sensor");
    }

    @Override
    public void execute(SensorContext context) {
      state.events.add("executed");
    }
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
