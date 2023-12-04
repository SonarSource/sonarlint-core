/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.analysis.container.analysis;

import java.io.File;
import org.sonar.api.utils.TempFolder;
import org.springframework.context.annotation.Bean;

public class AnalysisTempFolderProvider {

  private final NoTempFilesDuringAnalysis instance = new NoTempFilesDuringAnalysis();

  @Bean("TempFolder")
  public TempFolder provide() {
    return instance;
  }

  private static class NoTempFilesDuringAnalysis implements TempFolder {

    @Override
    public File newDir() {
      throw throwUOEFolder();
    }

    @Override
    public File newDir(String name) {
      throw throwUOEFolder();
    }

    private static UnsupportedOperationException throwUOEFolder() {
      return new UnsupportedOperationException("Don't create temp folders during analysis");
    }

    @Override
    public File newFile() {
      throw throwUOEFiles();
    }

    private static UnsupportedOperationException throwUOEFiles() {
      return new UnsupportedOperationException("Don't create temp files during analysis");
    }

    @Override
    public File newFile(String prefix, String suffix) {
      throw throwUOEFiles();
    }

  }
}
