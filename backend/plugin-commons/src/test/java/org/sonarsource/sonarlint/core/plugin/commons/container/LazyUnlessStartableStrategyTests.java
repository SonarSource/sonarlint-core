/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons.container;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.sonar.api.Startable;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.scanner.sensor.ProjectSensor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LazyUnlessStartableStrategyTests {
  private final LazyUnlessStartableStrategy postProcessor = new LazyUnlessStartableStrategy();

  @Test
  void sets_all_beans_lazy() {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerBeanDefinition("bean1", new RootBeanDefinition());
    assertThat(beanFactory.getBeanDefinition("bean1").isLazyInit()).isFalse();

    postProcessor.postProcessBeanFactory(beanFactory);
    assertThat(beanFactory.getBeanDefinition("bean1").isLazyInit()).isTrue();
  }

  @Test
  void startable_sensors_should_be_lazy_and_retain_their_lifecycle() {
    var lifecycle = new Lifecycle();
    var container = new SpringComponentContainer();
    container.add(lifecycle, StartableSensor.class, FailingStartableSensor.class);

    container.startComponents();
    assertThat(lifecycle.constructed).isZero();
    assertThat(lifecycle.started).isZero();
    var failures = new LinkedHashMap<String, Throwable>();
    assertThat(container.getComponentsByTypeSafely(ProjectSensor.class, failures::put)).hasSize(1).first().isInstanceOf(StartableSensor.class);
    assertThat(failures).hasSize(1);
    assertThat(failures.values().iterator().next()).hasRootCauseMessage("sensor constructor failure");
    assertThat(lifecycle.constructed).isEqualTo(2);
    assertThat(lifecycle.started).isOne();

    container.stopComponents();
    assertThat(lifecycle.stopped).isOne();
  }

  @Test
  void non_sensor_startables_should_remain_eager_and_fatal() {
    var container = new SpringComponentContainer();
    container.add(FailingStartable.class);

    assertThatThrownBy(container::startComponents).hasRootCauseMessage("core constructor failure");
    container.stopComponents();
  }

  private static class Lifecycle {
    private int constructed;
    private int started;
    private int stopped;
  }

  private static class StartableSensor implements ProjectSensor, Startable {
    private final Lifecycle lifecycle;

    public StartableSensor(Lifecycle lifecycle) {
      this.lifecycle = lifecycle;
      lifecycle.constructed++;
    }

    @Override
    public void start() {
      lifecycle.started++;
    }

    @Override
    public void stop() {
      lifecycle.stopped++;
    }

    @Override
    public void describe(SensorDescriptor descriptor) {
      // Only construction and lifecycle are tested here.
    }

    @Override
    public void execute(SensorContext context) {
      // Only construction and lifecycle are tested here.
    }
  }

  private static class FailingStartableSensor extends StartableSensor {
    public FailingStartableSensor(Lifecycle lifecycle) {
      super(lifecycle);
      throw new IllegalStateException("sensor constructor failure");
    }
  }

  private static class FailingStartable implements Startable {
    public FailingStartable() {
      throw new IllegalStateException("core constructor failure");
    }

    @Override
    public void start() {
      // Construction fails before start.
    }

    @Override
    public void stop() {
      // Construction fails before stop.
    }
  }

}
