/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.analysis;

import java.util.List;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;

/**
 * @deprecated since 10.2, replaced by {@link org.sonarsource.sonarlint.core.rpc.protocol.client.issue.QuickFixDto}
 * See {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService#analyzeFilesAndTrack(AnalyzeFilesAndTrackParams)}
 */
@Deprecated(since = "10.2")
public class QuickFixDto {

  private final List<FileEditDto> inputFileEdits;
  private final String message;

  public QuickFixDto(List<FileEditDto> inputFileEdits, String message) {
    this.inputFileEdits = inputFileEdits;
    this.message = message;
  }

  public List<FileEditDto> fileEdits() {
    return inputFileEdits;
  }

  public String message() {
    return message;
  }
}
