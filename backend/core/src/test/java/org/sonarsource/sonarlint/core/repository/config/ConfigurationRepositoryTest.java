/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
    configurationRepository.addOrReplace(new ConfigurationScope("id", null, true, "name"), BindingConfiguration.noBinding(true));

    var binding = configurationRepository.getEffectiveBinding("id");

    assertThat(binding).isEmpty();
  }

  @Test
  void it_should_consider_the_binding_configured_on_a_scope_as_effective() {
    configurationRepository.addOrReplace(new ConfigurationScope("id", null, true, "name"), new BindingConfiguration("connectionId", "projectKey", true));

    var binding = configurationRepository.getEffectiveBinding("id");

    assertThat(binding)
      .hasValueSatisfying(b -> {
        assertThat(b.connectionId()).isEqualTo("connectionId");
        assertThat(b.sonarProjectKey()).isEqualTo("projectKey");
      });
  }

  @Test
  void it_should_get_the_effective_binding_from_parent_if_child_is_unbound() {
    configurationRepository.addOrReplace(new ConfigurationScope("parentId", null, true, "name"), new BindingConfiguration("connectionId", "projectKey", true));
    configurationRepository.addOrReplace(new ConfigurationScope("id", "parentId", true, "name"), new BindingConfiguration(null, null, true));

    var binding = configurationRepository.getEffectiveBinding("id");

    assertThat(binding)
      .hasValueSatisfying(b -> {
        assertThat(b.connectionId()).isEqualTo("connectionId");
        assertThat(b.sonarProjectKey()).isEqualTo("projectKey");
      });
  }

  @Test
  void it_should_not_detect_connected_mode_if_unbound_and_no_parent() {
    configurationRepository.addOrReplace(new ConfigurationScope("id1", null, true, "name1"), BindingConfiguration.noBinding(true));

    assertThat(configurationRepository.isConnectedMode("id1")).isFalse();
  }

  @Test
  void it_should_detect_connected_mode_if_bound() {
    configurationRepository.addOrReplace(new ConfigurationScope("id2", null, true, "name2"), new BindingConfiguration("conn", "proj", true));

    assertThat(configurationRepository.isConnectedMode("id2")).isTrue();
  }

  @Test
  void it_should_detect_connected_mode_from_parent() {
    configurationRepository.addOrReplace(new ConfigurationScope("id2", null, true, "name2"), new BindingConfiguration("conn", "proj", true));
    configurationRepository.addOrReplace(new ConfigurationScope("child", "id2", true, "childName"), BindingConfiguration.noBinding(true));

    assertThat(configurationRepository.isConnectedMode("child")).isTrue();
  }

  @Test
  void it_should_not_detect_connected_mode_from_unknown_config_scope() {
    assertThat(configurationRepository.isConnectedMode("unknown")).isFalse();
  }

}
