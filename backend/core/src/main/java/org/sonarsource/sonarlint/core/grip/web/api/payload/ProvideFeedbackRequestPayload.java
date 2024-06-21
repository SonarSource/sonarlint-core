/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.grip.web.api.payload;

import java.util.UUID;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestionReviewStatus;

public class ProvideFeedbackRequestPayload {
  private final String ruleKey;
  private final SuggestionReviewStatus reviewStatus;
  private final String rating;
  private final String comments;
  private final ContextPayload context;

  public ProvideFeedbackRequestPayload(String ruleKey, SuggestionReviewStatus reviewStatus, String rating, String comment, ContextPayload context) {
    this.ruleKey = ruleKey;
    this.reviewStatus = reviewStatus;
    this.rating = rating;
    this.comments = comment;
    this.context = context;
  }

  public static class ContextPayload {
    private final String promptVersion;
    private final UUID correlationId;
    private final long responseTime;
    private final String response;

    public ContextPayload(String promptVersion, UUID correlationId, long responseTime, String response) {
      this.promptVersion = promptVersion;
      this.correlationId = correlationId;
      this.responseTime = responseTime;
      this.response = response;
    }
  }
}
