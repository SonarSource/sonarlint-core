/*
 * SonarLint Core Library
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.global;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.GlobalConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalContainerTest {

  private GlobalContainer createContainer(List<Object> extensions) {
    GlobalContainer container = GlobalContainer.create(extensions);
    container.doBeforeStart();
    return container;
  }

  @Test
  public void should_add_components() {
    GlobalContainer container = createContainer(Arrays.<Object>asList(new GlobalConfiguration(null, null)));

    assertThat(container.getComponentByType(TempFolder.class)).isNotNull();
  }

  @Test
  public void should_add_bootstrap_extensions() {
    GlobalContainer container = createContainer(Lists.newArrayList(Foo.class, new Bar()));

    assertThat(container.getComponentByType(Foo.class)).isNotNull();
    assertThat(container.getComponentByType(Bar.class)).isNotNull();
  }

  @BatchSide
  public static class Foo {

  }

  @BatchSide
  public static class Bar {

  }

}
