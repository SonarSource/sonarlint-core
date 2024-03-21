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
package org.sonarsource.sonarlint.core.rpc.protocol.client.analysis;

import java.net.URI;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

public class RawIssueLocationDto {
  private final TextRangeDto textRange;
  private final String message;
  private final URI fileUri;
  private final String codeSnippet;

  public RawIssueLocationDto(TextRangeDto textRange, String message, URI fileUri, String codeSnippet) {
    this.textRange = textRange;
    this.message = message;
    this.fileUri = fileUri;
    this.codeSnippet = codeSnippet;
  }

  public TextRangeDto getTextRange() {
    return textRange;
  }

  public String getMessage() {
    return message;
  }

  public URI getFileUri() {
    return fileUri;
  }

  public String getCodeSnippet() {
    return codeSnippet;
  }
}
