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
package org.sonarsource.sonarlint.core.container.storage;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ProjectList.Project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AllProjectReaderTest {
  private StorageReader storageReader;

  @Before
  public void setUp() {
    storageReader = mock(StorageReader.class);
  }

  @Test
  public void should_get_modules() {
    ProjectList.Builder list = ProjectList.newBuilder();
    Project m1 = Project.newBuilder().setKey("module1").build();
    list.getMutableProjectsByKey().put("module1", m1);

    when(storageReader.readProjectList()).thenReturn(list.build());

    AllProjectReader modulesReader = new AllProjectReader(storageReader);
    assertThat(modulesReader.get()).containsOnlyKeys("module1");
  }
}
