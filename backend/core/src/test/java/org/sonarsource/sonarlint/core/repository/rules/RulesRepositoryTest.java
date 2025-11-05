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
package org.sonarsource.sonarlint.core.repository.rules;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rules.RulesExtractionHelper;
import org.sonarsource.sonarlint.core.serverconnection.repository.ServerInfoRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RulesRepositoryTest {

  @Test
  void it_should_not_touch_storage_after_rules_are_lazily_loaded_in_connected_mode() {
    var serverInfoRepository = mock(ServerInfoRepository.class);
    var rulesRepository = new RulesRepository(mock(RulesExtractionHelper.class), mock(ConfigurationRepository.class), serverInfoRepository);
    when(serverInfoRepository.read("connection")).thenReturn(Optional.empty());
    rulesRepository.getRule("connection", "rule");
    reset(serverInfoRepository);

    rulesRepository.getRule("connection", "rule");

    verifyNoInteractions(serverInfoRepository);
  }

}
