/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class InitializeParamsTests {

  @Test
  void should_replace_null_collections_by_empty() {
    var params = new InitializeParams(null, null,null, null, null, null, null, null, null, null, null, null, null, null, null, null, false, null);
    assertNotNull(params.getEmbeddedPluginPaths());
    assertNotNull(params.getConnectedModeEmbeddedPluginPathsByKey());
    assertNotNull(params.getEnabledLanguagesInStandaloneMode());
    assertNotNull(params.getExtraEnabledLanguagesInConnectedMode());
    assertNotNull(params.getSonarQubeConnections());
    assertNotNull(params.getSonarCloudConnections());
    assertNotNull(params.getStandaloneRuleConfigByKey());
    assertNotNull(params.getDisabledLanguagesForAnalysis());
  }

}
