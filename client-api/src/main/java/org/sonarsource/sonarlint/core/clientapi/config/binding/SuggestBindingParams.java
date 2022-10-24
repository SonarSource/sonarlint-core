/*
 * SonarLint Core - Client API
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.clientapi.config.binding;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class SuggestBindingParams {

  Map<String, List<BindingSuggestionDto>> suggestions = new HashMap<>();

  public SuggestBindingParams(@NonNull Map<String, List<BindingSuggestionDto>> suggestions) {
    this.suggestions = suggestions;
  }

  @NonNull
  public Map<String, List<BindingSuggestionDto>> getSuggestions() {
    return suggestions;
  }
}
