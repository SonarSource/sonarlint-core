/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.sca;

import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * Response of an on-demand SCA project analysis.
 * <p>
 * {@link #getParsedFiles()} and {@link #getErrors()} expose local-analysis diagnostics that have no equivalent in the
 * server-tracked model.
 * </p>
 */
public class AnalyzeDependencyRiskProjectResponse {
  private final List<String> parsedFiles;
  private final List<AnalyzeDependencyRiskProjectErrorDto> errors;

  public AnalyzeDependencyRiskProjectResponse(List<String> parsedFiles, List<AnalyzeDependencyRiskProjectErrorDto> errors) {
    this.parsedFiles = parsedFiles;
    this.errors = errors;
  }


  public List<String> getParsedFiles() {
    return parsedFiles;
  }

  public List<AnalyzeDependencyRiskProjectErrorDto> getErrors() {
    return errors;
  }

  public static class AnalyzeDependencyRiskProjectErrorDto {
    private final String id;
    private final String code;
    @Nullable
    private final String path;
    private final String message;

    public AnalyzeDependencyRiskProjectErrorDto(String id, String code, @Nullable String path, String message) {
      this.id = id;
      this.code = code;
      this.path = path;
      this.message = message;
    }

    public String getId() {
      return id;
    }

    public String getCode() {
      return code;
    }

    @CheckForNull
    public String getPath() {
      return path;
    }

    public String getMessage() {
      return message;
    }
  }

}
