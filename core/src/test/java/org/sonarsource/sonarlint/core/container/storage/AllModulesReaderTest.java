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
package org.sonarsource.sonarlint.core.container.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList.Module;

public class AllModulesReaderTest {
  private StorageReader storageReader;

  @Before
  public void setUp() {
    storageReader = mock(StorageReader.class);
  }

  @Test
  public void should_get_modules() {
    ModuleList.Builder list = ModuleList.newBuilder();
    Module m1 = Module.newBuilder().setKey("module1").build();
    list.getMutableModulesByKey().put("module1", m1);

    when(storageReader.readModuleList()).thenReturn(list.build());

    AllModulesReader modulesReader = new AllModulesReader(storageReader);
    assertThat(modulesReader.get()).containsOnlyKeys("module1");
  }
}
