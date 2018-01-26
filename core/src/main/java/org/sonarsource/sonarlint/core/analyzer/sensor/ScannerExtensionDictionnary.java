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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang.ClassUtils;
import org.sonar.api.batch.CheckProject;
import org.sonar.api.batch.DependedUpon;
import org.sonar.api.batch.DependsUpon;
import org.sonar.api.batch.Phase;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.resources.Project;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.dag.DirectAcyclicGraph;
import org.sonarsource.sonarlint.core.container.ComponentContainer;

public class ScannerExtensionDictionnary {

  private final ComponentContainer componentContainer;
  private final SensorContext sensorContext;
  private final SensorOptimizer sensorOptimizer;

  public ScannerExtensionDictionnary(ComponentContainer componentContainer, DefaultSensorContext sensorContext, SensorOptimizer sensorOptimizer) {
    this.componentContainer = componentContainer;
    this.sensorContext = sensorContext;
    this.sensorOptimizer = sensorOptimizer;
  }

  public <T> Collection<T> select(Class<T> type, @Nullable Project project, boolean sort) {
    List<T> result = getFilteredExtensions(type, project);
    if (sort) {
      return sort(result);
    }
    return result;
  }

  private static Phase.Name evaluatePhase(Object extension) {
    Object extensionToEvaluate;
    if (extension instanceof SensorWrapper) {
      extensionToEvaluate = ((SensorWrapper) extension).wrappedSensor();
    } else {
      extensionToEvaluate = extension;
    }
    Phase phaseAnnotation = AnnotationUtils.getAnnotation(extensionToEvaluate, Phase.class);
    if (phaseAnnotation != null) {
      return phaseAnnotation.name();
    }
    return Phase.Name.DEFAULT;
  }

  private <T> List<T> getFilteredExtensions(Class<T> type, @Nullable Project project) {
    List<T> result = new ArrayList<>();
    for (Object extension : getExtensions(type)) {
      if (org.sonar.api.batch.Sensor.class.equals(type) && extension instanceof Sensor) {
        extension = new SensorWrapper((Sensor) extension, sensorContext, sensorOptimizer);
      }
      if (shouldKeep(type, extension, project)) {
        result.add((T) extension);
      }
    }
    if (org.sonar.api.batch.Sensor.class.equals(type)) {
      // Retrieve new Sensors and wrap then in SensorWrapper
      for (Object extension : getExtensions(Sensor.class)) {
        extension = new SensorWrapper((Sensor) extension, sensorContext, sensorOptimizer);
        if (shouldKeep(type, extension, project)) {
          result.add((T) extension);
        }
      }
    }
    return result;
  }

  protected List<Object> getExtensions(Class type) {
    List<Object> extensions = new ArrayList<>();
    completeBatchExtensions(componentContainer, extensions, type);
    return extensions;
  }

  private static void completeBatchExtensions(@Nullable ComponentContainer container, List<Object> extensions, Class type) {
    if (container != null) {
      extensions.addAll(container.getComponentsByType(type));
      completeBatchExtensions(container.getParent(), extensions, type);
    }
  }

  public static <T> Collection<T> sort(Collection<T> extensions) {
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
    List<T> sortedList = dag.sort();
    return sortedList.stream()
      .filter(extensions::contains)
      .collect(Collectors.toList());
  }

  /**
   * Extension dependencies
   */
  private static <T> List<Object> getDependencies(T extension) {
    List<Object> result = new ArrayList<>();
    result.addAll(evaluateAnnotatedClasses(extension, DependsUpon.class));
    return result;
  }

  /**
   * Objects that depend upon this extension.
   */
  public static <T> List<Object> getDependents(T extension) {
    List<Object> result = new ArrayList<>();
    result.addAll(evaluateAnnotatedClasses(extension, DependedUpon.class));
    return result;
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

  protected static List<Object> evaluateAnnotatedClasses(Object extension, Class<? extends Annotation> annotation) {
    List<Object> results = new ArrayList<>();
    Class aClass = extension.getClass();
    while (aClass != null) {
      evaluateClass(aClass, annotation, results);
      aClass = aClass.getSuperclass();
    }

    return results;
  }

  private static void evaluateClass(Class extensionClass, Class annotationClass, List<Object> results) {
    Annotation annotation = extensionClass.getAnnotation(annotationClass);
    if (annotation != null) {
      if (annotation.annotationType().isAssignableFrom(DependsUpon.class)) {
        results.addAll(Arrays.asList(((DependsUpon) annotation).value()));

      } else if (annotation.annotationType().isAssignableFrom(DependedUpon.class)) {
        results.addAll(Arrays.asList(((DependedUpon) annotation).value()));
      }
    }

    Class[] interfaces = extensionClass.getInterfaces();
    for (Class anInterface : interfaces) {
      evaluateClass(anInterface, annotationClass, results);
    }
  }

  private static boolean shouldKeep(Class type, Object extension, @Nullable Project project) {
    boolean keep = (ClassUtils.isAssignable(extension.getClass(), type)
      || (org.sonar.api.batch.Sensor.class.equals(type) && ClassUtils.isAssignable(extension.getClass(), Sensor.class)));
    if (keep && project != null && ClassUtils.isAssignable(extension.getClass(), CheckProject.class)) {
      keep = ((CheckProject) extension).shouldExecuteOnProject(project);
    }
    return keep;
  }
}
