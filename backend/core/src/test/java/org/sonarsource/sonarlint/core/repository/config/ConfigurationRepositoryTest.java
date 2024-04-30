/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.repository.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationRepositoryTest {

  private ConfigurationRepository configurationRepository;

  @BeforeEach
  void prepare() {
    configurationRepository = new ConfigurationRepository();
  }

  @Test
  void it_should_not_find_any_binding_on_an_unknown_scope() {
    var binding = configurationRepository.getEffectiveBinding("id");

    assertThat(binding).isEmpty();
  }

  @Test
  void it_should_not_find_any_binding_on_an_unbound_scope() {
    configurationRepository.addOrReplace(ConfigurationScope.builder().setId("id").setParentId(null).setBindable(true).setName("name").setSetFocusOnNewCode(false).build(), new BindingConfiguration(null, null, true));

    var binding = configurationRepository.getEffectiveBinding("id");

    assertThat(binding).isEmpty();
  }

  @Test
  void it_should_consider_the_binding_configured_on_a_scope_as_effective() {
    configurationRepository.addOrReplace(ConfigurationScope.builder().setId("id").setParentId(null).setBindable(true).setName("name").setSetFocusOnNewCode(false).build(), new BindingConfiguration("connectionId", "projectKey", true));

    var binding = configurationRepository.getEffectiveBinding("id");

    assertThat(binding)
      .hasValueSatisfying(b -> {
        assertThat(b.getConnectionId()).isEqualTo("connectionId");
        assertThat(b.getSonarProjectKey()).isEqualTo("projectKey");
      });
  }

  @Test
  void it_should_get_the_effective_binding_from_parent_if_child_is_unbound() {
    configurationRepository.addOrReplace(ConfigurationScope.builder().setId("parentId").setParentId(null).setBindable(true).setName("name").setSetFocusOnNewCode(false).build(), new BindingConfiguration("connectionId", "projectKey", true));
    configurationRepository.addOrReplace(ConfigurationScope.builder().setId("id").setParentId("parentId").setBindable(true).setName("name").setSetFocusOnNewCode(false).build(), new BindingConfiguration(null, null, true));

    var binding = configurationRepository.getEffectiveBinding("id");

    assertThat(binding)
      .hasValueSatisfying(b -> {
        assertThat(b.getConnectionId()).isEqualTo("connectionId");
        assertThat(b.getSonarProjectKey()).isEqualTo("projectKey");
      });
  }

}
