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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.grip;

import com.google.gson.annotations.JsonAdapter;
import java.util.List;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.EitherFixSuggestionAdapterFactory;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

public class SuggestFixesResponse {
  private final List<Result> results;

  public SuggestFixesResponse(List<Result> results) {
    this.results = results;
  }

  public List<Result> getResults() {
    return results;
  }

  public static class Result {
    @JsonAdapter(EitherFixSuggestionAdapterFactory.class)
    private final Either<SuggestionError, SuggestionDto> suggestions;

    public Result(Either<SuggestionError, SuggestionDto> suggestions) {
      this.suggestions = suggestions;
    }

    public Either<SuggestionError, SuggestionDto> getSuggestions() {
      return suggestions;
    }
  }
}
