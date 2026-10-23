/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DidUpdateBindingParamsTests {

  @Test
  void constructor_keeps_mode_and_origin_null() {
    var dto = new BindingConfigurationDto("conn", "proj", false);

    var params = new DidUpdateBindingParams("scope", dto);

    assertThat(params.getConfigScopeId()).isEqualTo("scope");
    assertThat(params.getUpdatedBinding()).isEqualTo(dto);
    assertThat(params.getBindingMode()).isNull();
    assertThat(params.getOrigin()).isNull();
  }
}
