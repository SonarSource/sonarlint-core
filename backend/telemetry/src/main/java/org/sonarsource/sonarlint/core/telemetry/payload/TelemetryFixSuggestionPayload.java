/*
 * SonarLint Core - Telemetry
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
package org.sonarsource.sonarlint.core.telemetry.payload;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiSuggestionSource;

public record TelemetryFixSuggestionPayload(@SerializedName("suggestion_id") String suggestionId,
                                            @SerializedName("count_snippets") int countSnippets,
                                            @SerializedName("ai_fix_suggestion_provider") AiSuggestionSource aiFixSuggestionProvider,
                                            @SerializedName("snippets") List<TelemetryFixSuggestionResolvedPayload> snippets,
                                            @SerializedName("was_ai_fix_suggestion_generated_from_ide") boolean wasAiFixSuggestionGeneratedFromIde) {
}
