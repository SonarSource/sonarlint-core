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

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.sonar.api.BatchExtension;
import org.sonar.api.batch.BuildBreaker;
import org.sonar.api.batch.CheckProject;
import org.sonar.api.batch.Decorator;
import org.sonar.api.batch.DependedUpon;
import org.sonar.api.batch.DependsUpon;
import org.sonar.api.batch.Phase;
import org.sonar.api.batch.PostJob;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.bootstrap.ProjectDefinition;
import org.sonar.api.resources.Project;
import org.sonarsource.sonarlint.core.container.ComponentContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class ScannerExtensionDictionnaryTest {

  private ScannerExtensionDictionnary newSelector(Object... extensions) {
    ComponentContainer iocContainer = new ComponentContainer();
    for (Object extension : extensions) {
      iocContainer.addSingleton(extension);
    }
    return new ScannerExtensionDictionnary(iocContainer, mock(DefaultSensorContext.class), mock(SensorOptimizer.class));
  }

  @Test
  public void testGetFilteredExtensions() {
    Sensor sensor1 = new FakeSensor();
    Sensor sensor2 = new FakeSensor();
    Decorator decorator = mock(Decorator.class);

    ScannerExtensionDictionnary selector = newSelector(sensor1, sensor2, decorator);
    Collection<Sensor> sensors = selector.select(Sensor.class, null, true);

    assertThat(sensors).containsOnly(sensor1, sensor2);
  }

  @Test
  public void shouldSearchInParentContainers() {
    Sensor a = new FakeSensor();
    Sensor b = new FakeSensor();
    Sensor c = new FakeSensor();

    ComponentContainer grandParent = new ComponentContainer();
    grandParent.addSingleton(a);

    ComponentContainer parent = grandParent.createChild();
    parent.addSingleton(b);

    ComponentContainer child = parent.createChild();
    child.addSingleton(c);

    ScannerExtensionDictionnary dictionnary = new ScannerExtensionDictionnary(child, mock(DefaultSensorContext.class), mock(SensorOptimizer.class));
    assertThat(dictionnary.select(Sensor.class, null, true)).containsOnly(a, b, c);
  }

  @Test
  public void useClassAnnotationsToSortExtensions() {
    BatchExtension a = new ClassDependedUpon();
    BatchExtension b = new ClassDependsUpon();

    ScannerExtensionDictionnary selector = newSelector(a, b);
    List<BatchExtension> extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);

    // different initial order
    selector = newSelector(b, a);
    extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);
  }

  @Test
  public void useClassAnnotationsOnInterfaces() {
    BatchExtension a = new InterfaceDependedUpon() {
    };
    BatchExtension b = new InterfaceDependsUpon() {
    };

    ScannerExtensionDictionnary selector = newSelector(a, b);
    List<BatchExtension> extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);

    // different initial order
    selector = newSelector(b, a);
    extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);
  }

  @Test
  public void checkProject() {
    BatchExtension ok = new CheckProjectOK();
    BatchExtension ko = new CheckProjectKO();

    ScannerExtensionDictionnary selector = newSelector(ok, ko);
    List<BatchExtension> extensions = Lists.newArrayList(selector.select(BatchExtension.class,
      new Project(ProjectDefinition.create().setKey("key")), true));

    assertThat(extensions).hasSize(1);
    assertThat(extensions.get(0)).isInstanceOf(CheckProjectOK.class);
  }

  @Test
  public void dependsUponPhase() {
    BatchExtension pre = new PreSensor();
    BatchExtension post = new PostSensor();

    ScannerExtensionDictionnary selector = newSelector(post, pre);
    List extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(pre);
    assertThat(extensions.get(1)).isEqualTo(post);
  }

  @Test
  public void dependsUponInheritedPhase() {
    BatchExtension pre = new PreSensorSubclass();
    BatchExtension post = new PostSensorSubclass();

    ScannerExtensionDictionnary selector = newSelector(post, pre);
    List extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(pre);
    assertThat(extensions.get(1)).isEqualTo(post);
  }

  @Test
  public void buildStatusCheckersAreExecutedAfterOtherPostJobs() {
    BuildBreaker checker = new BuildBreaker() {
      public void executeOn(Project project, SensorContext context) {
      }
    };

    ScannerExtensionDictionnary selector = newSelector(new FakePostJob(), checker, new FakePostJob());
    List extensions = Lists.newArrayList(selector.select(PostJob.class, null, true));

    assertThat(extensions).hasSize(3);
    assertThat(extensions.get(2)).isEqualTo(checker);
  }

  class FakeSensor implements Sensor {

    public void analyse(Project project, SensorContext context) {

    }

    public boolean shouldExecuteOnProject(Project project) {
      return true;
    }
  }

  @DependsUpon("flag")
  class ClassDependsUpon implements BatchExtension {
  }

  @DependedUpon("flag")
  class ClassDependedUpon implements BatchExtension {
  }

  @DependsUpon("flag")
  interface InterfaceDependsUpon extends BatchExtension {
  }

  @DependedUpon("flag")
  interface InterfaceDependedUpon extends BatchExtension {
  }

  @Phase(name = Phase.Name.PRE)
  class PreSensor implements BatchExtension {

  }

  class PreSensorSubclass extends PreSensor {

  }

  @Phase(name = Phase.Name.POST)
  class PostSensor implements BatchExtension {

  }

  class PostSensorSubclass extends PostSensor {

  }

  class CheckProjectOK implements BatchExtension, CheckProject {
    public boolean shouldExecuteOnProject(Project project) {
      return true;
    }
  }

  class CheckProjectKO implements BatchExtension, CheckProject {
    public boolean shouldExecuteOnProject(Project project) {
      return false;
    }
  }

  private class FakePostJob implements PostJob {
    public void executeOn(Project project, SensorContext context) {
    }
  }
}
