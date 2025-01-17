/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry.payload;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiSuggestionSource;

public class TelemetryFixSuggestionPayload {
  @SerializedName("suggestion_id")
  private final String suggestionId;

  @SerializedName("count_snippets")
  private final int countSnippets;

  @SerializedName("opened_from")
  private final AiSuggestionSource openedFrom;

  @SerializedName("snippets")
  private final List<TelemetryFixSuggestionResolvedPayload> snippets;

  public TelemetryFixSuggestionPayload(String suggestionId, int countSnippets, AiSuggestionSource openedFrom,
    List<TelemetryFixSuggestionResolvedPayload> snippets) {
    this.suggestionId = suggestionId;
    this.countSnippets = countSnippets;
    this.openedFrom = openedFrom;
    this.snippets = snippets;
  }

  public String getSuggestionId() {
    return suggestionId;
  }

  public List<TelemetryFixSuggestionResolvedPayload> getSnippets() {
    return snippets;
  }

  public int getCountSnippets() {
    return countSnippets;
  }

  public AiSuggestionSource getOpenedFrom() {
    return openedFrom;
  }
}
