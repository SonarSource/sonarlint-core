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
package org.sonarsource.sonarlint.core.container.model;

import org.junit.Before;
import org.junit.Test;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList.Module;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultRemoteModuleTest {
  private DefaultRemoteModule remoteModule;
  private Module module;

  @Before
  public void setUp() {
    module = Module.newBuilder()
      .setKey("key")
      .setName("name")
      .setQu("TRK")
      .build();
    remoteModule = new DefaultRemoteModule(module, null);
  }

  @Test
  public void testGetters() {
    assertThat(remoteModule.getKey()).isEqualTo("key");
    assertThat(remoteModule.getName()).isEqualTo("name");
    assertThat(remoteModule.isRoot()).isTrue();
  }

}
