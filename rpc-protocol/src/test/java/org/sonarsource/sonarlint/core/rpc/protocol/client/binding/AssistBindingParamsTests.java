/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.binding;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionOrigin;

import static org.assertj.core.api.Assertions.assertThat;

class AssistBindingParamsTests {

  @Test
  void new_ctor_sets_fields_and_isFromSharedConfiguration_true_when_origin_is_shared_configuration() {
    var params = new AssistBindingParams("conn1", "proj", "scope1", BindingSuggestionOrigin.SHARED_CONFIGURATION);

    assertThat(params.getConnectionId()).isEqualTo("conn1");
    assertThat(params.getProjectKey()).isEqualTo("proj");
    assertThat(params.getConfigScopeId()).isEqualTo("scope1");
    assertThat(params.isFromSharedConfiguration()).isTrue();
  }

  @Test
  void new_ctor_sets_isFromSharedConfiguration_false_for_non_shared_origins_properties_file() {
    var params = new AssistBindingParams("conn2", "proj2", "scope2", BindingSuggestionOrigin.PROPERTIES_FILE);

    assertThat(params.getConnectionId()).isEqualTo("conn2");
    assertThat(params.getProjectKey()).isEqualTo("proj2");
    assertThat(params.getConfigScopeId()).isEqualTo("scope2");
    assertThat(params.isFromSharedConfiguration()).isFalse();
  }

  @Test
  void new_ctor_sets_isFromSharedConfiguration_false_for_non_shared_origins_project_name() {
    var params = new AssistBindingParams("conn3", "proj3", "scope3", BindingSuggestionOrigin.PROJECT_NAME);

    assertThat(params.getConnectionId()).isEqualTo("conn3");
    assertThat(params.getProjectKey()).isEqualTo("proj3");
    assertThat(params.getConfigScopeId()).isEqualTo("scope3");
    assertThat(params.isFromSharedConfiguration()).isFalse();
  }

  @Test
  void deprecated_ctor_keeps_boolean_semantics_and_sets_fields() {
    var params = new AssistBindingParams("conn4", "proj4", "scope4", BindingSuggestionOrigin.SHARED_CONFIGURATION);

    assertThat(params.getConnectionId()).isEqualTo("conn4");
    assertThat(params.getProjectKey()).isEqualTo("proj4");
    assertThat(params.getConfigScopeId()).isEqualTo("scope4");
    assertThat(params.isFromSharedConfiguration()).isTrue();

    var paramsFalse = new AssistBindingParams("conn5", "proj5", "scope5", BindingSuggestionOrigin.PROJECT_NAME);
    assertThat(paramsFalse.isFromSharedConfiguration()).isFalse();
  }
}
