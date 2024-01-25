/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.embedded.server;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingSuggestionDto;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ShowHotspotOrIssueRequestHandlerTests {

  @Test
  void should_find_configScope_from_binding_suggestions() {
    var suggestions = Map.of("configScopeId", List.of(new BindingSuggestionDto("connectionId", "projectKey", "Project Name")));

    var configScopeId = ShowHotspotOrIssueRequestHandler.findSingleConfigScopeIdFromBindingSuggestions(suggestions, "projectKey");

    assertThat(configScopeId).isEqualTo("configScopeId");
  }

  @Test
  void should_return_null_if_more_than_one_match() {
    var suggestions = Map.of("configScopeId1", List.of(new BindingSuggestionDto("connectionId", "projectKey", "Project Name")),
      "configScopeId2", List.of(new BindingSuggestionDto("connectionId", "projectKey", "Project Name")));

    var configScopeId = ShowHotspotOrIssueRequestHandler.findSingleConfigScopeIdFromBindingSuggestions(suggestions, "projectKey");

    assertThat(configScopeId).isNull();
  }
}
