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

import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class SuggestFixResponse {
  private final UUID correlationId;
  private final String text;
  private final SuggestedFixDto suggestedFix;

  public SuggestFixResponse(UUID correlationId, String text, @Nullable SuggestedFixDto suggestedFix) {
    this.correlationId = correlationId;
    this.text = text;
    this.suggestedFix = suggestedFix;
  }

  public UUID getCorrelationId() {
    return correlationId;
  }

  public String getText() {
    return text;
  }

  @CheckForNull
  public SuggestedFixDto getSuggestedFix() {
    return suggestedFix;
  }
}
