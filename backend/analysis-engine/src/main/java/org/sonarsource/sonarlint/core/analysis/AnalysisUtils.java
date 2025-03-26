/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis;

import java.util.Objects;
import org.sonarsource.sonarlint.core.analysis.command.AnalyzeCommand;
import org.sonarsource.sonarlint.core.analysis.command.Command;

public class AnalysisUtils {

  private AnalysisUtils() {
    // util
  }

  public static boolean isSimilarAnalysis(Command commandA, Command commandB) {
    if (!(commandA instanceof AnalyzeCommand analyzeCommandA) || !(commandB instanceof AnalyzeCommand analyzeCommandB)) {
      return false;
    }
    var triggerTypesMatch = analyzeCommandA.getTriggerType() == analyzeCommandB.getTriggerType();
    var filesMatch = Objects.equals(analyzeCommandA.getFiles(), analyzeCommandB.getFiles());
    var extraPropertiesMatch = Objects.equals(analyzeCommandA.getExtraProperties(), analyzeCommandB.getExtraProperties());
    return triggerTypesMatch && filesMatch && extraPropertiesMatch;
  }
}
