/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.fix;

import java.util.List;

public class BatchFixSuggestionDto {

  private final String suggestionId;
  private final String explanation;
  private final List<FileEditDto> fileEdits;

  public BatchFixSuggestionDto(String suggestionId, String explanation, List<FileEditDto> fileEdits) {
    this.suggestionId = suggestionId;
    this.explanation = explanation;
    this.fileEdits = fileEdits;
  }

  public String suggestionId() {
    return suggestionId;
  }

  public String explanation() {
    return explanation;
  }

  public List<FileEditDto> fileEdits() {
    return fileEdits;
  }

}
