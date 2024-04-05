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
package org.sonarsource.sonarlint.core.analysis.sonarapi.noop;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.sensor.symbol.NewSymbol;
import org.sonar.api.batch.sensor.symbol.NewSymbolTable;

public class NoOpNewSymbolTable implements NewSymbolTable, NewSymbol {
  @Override
  public void save() {
    // Do nothing
  }

  @Override
  public NoOpNewSymbolTable onFile(InputFile inputFile) {
    // Do nothing
    return this;
  }

  @Override
  public NoOpNewSymbolTable newSymbol(int startLine, int startLineOffset, int endLine, int endLineOffset) {
    // Do nothing
    return this;
  }

  @Override
  public NoOpNewSymbolTable newSymbol(TextRange range) {
    // Do nothing
    return this;
  }

  @Override
  public NoOpNewSymbolTable newReference(int startLine, int startLineOffset, int endLine, int endLineOffset) {
    // Do nothing
    return this;
  }

  @Override
  public NoOpNewSymbolTable newReference(TextRange range) {
    // Do nothing
    return this;
  }

}
