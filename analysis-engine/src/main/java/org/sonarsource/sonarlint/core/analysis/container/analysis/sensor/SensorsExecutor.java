/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2021 SonarSource SA
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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.sonar.api.batch.DependedUpon;
import org.sonar.api.batch.DependsUpon;
import org.sonar.api.batch.Phase;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.dag.DirectAcyclicGraph;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.analysis.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.analysis.util.StringUtils;

import static java.util.Arrays.asList;

/**
 * Execute Sensors.
 */
public class SensorsExecutor {

  private static final Logger LOG = Loggers.get(SensorsExecutor.class);

  private final SensorOptimizer sensorOptimizer;
  private final ProgressWrapper progress;
  private final Sensor[] sensors;
  private final DefaultSensorContext context;

  public SensorsExecutor(DefaultSensorContext context, SensorOptimizer sensorOptimizer, ProgressWrapper progress) {
    this(context, sensorOptimizer, progress, new Sensor[0]);
  }

  public SensorsExecutor(DefaultSensorContext context, SensorOptimizer sensorOptimizer, ProgressWrapper progress, Sensor[] sensors) {
    this.context = context;
    this.sensors = sensors;
    this.sensorOptimizer = sensorOptimizer;
    this.progress = progress;
  }

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
    String name = descriptor.name() != null ? descriptor.name() : StringUtils.describe(sensor);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Execute Sensor: {}", name);
    }
    try {
      sensor.execute(context);
    } catch (Throwable e) {
      LOG.error("Error while executing Sensor {}", name, e);
    }
  }

  private static <T> Collection<T> sort(Collection<T> extensions) {
    DirectAcyclicGraph dag = new DirectAcyclicGraph();

    for (T extension : extensions) {
      dag.add(extension);
      for (Object dependency : getDependencies(extension)) {
        dag.add(extension, dependency);
      }
      for (Object generates : getDependents(extension)) {
        dag.add(generates, extension);
      }
      completePhaseDependencies(dag, extension);
    }
    List<?> sortedList = dag.sort();

    return (Collection<T>) sortedList.stream()
      .filter(extensions::contains)
      .collect(Collectors.toList());
  }

  /**
   * Extension dependencies
   */
  private static <T> List<Object> getDependencies(T extension) {
    return new ArrayList<>(evaluateAnnotatedClasses(extension, DependsUpon.class));
  }

  /**
   * Objects that depend upon this extension.
   */
  private static <T> List<Object> getDependents(T extension) {
    return new ArrayList<>(evaluateAnnotatedClasses(extension, DependedUpon.class));
  }

  private static void completePhaseDependencies(DirectAcyclicGraph dag, Object extension) {
    Phase.Name phase = evaluatePhase(extension);
    dag.add(extension, phase);
    for (Phase.Name name : Phase.Name.values()) {
      if (phase.compareTo(name) < 0) {
        dag.add(name, extension);
      } else if (phase.compareTo(name) > 0) {
        dag.add(extension, name);
      }
    }
  }

  private static Phase.Name evaluatePhase(Object extension) {
    Phase phaseAnnotation = AnnotationUtils.getAnnotation(extension, Phase.class);
    if (phaseAnnotation != null) {
      return phaseAnnotation.name();
    }
    return Phase.Name.DEFAULT;
  }

  static List<Object> evaluateAnnotatedClasses(Object extension, Class<? extends Annotation> annotation) {
    List<Object> results = new ArrayList<>();
    Class<?> aClass = extension.getClass();
    while (aClass != null) {
      evaluateClass(aClass, annotation, results);
      aClass = aClass.getSuperclass();
    }

    return results;
  }

  private static void evaluateClass(Class<?> extensionClass, Class<? extends Annotation> annotationClass, List<Object> results) {
    Annotation annotation = extensionClass.getAnnotation(annotationClass);
    if (annotation != null) {
      if (annotation.annotationType().isAssignableFrom(DependsUpon.class)) {
        results.addAll(Arrays.asList(((DependsUpon) annotation).value()));

      } else if (annotation.annotationType().isAssignableFrom(DependedUpon.class)) {
        results.addAll(Arrays.asList(((DependedUpon) annotation).value()));
      }
    }

    Class<?>[] interfaces = extensionClass.getInterfaces();
    for (Class<?> anInterface : interfaces) {
      evaluateClass(anInterface, annotationClass, results);
    }
  }
}
