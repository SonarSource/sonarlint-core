/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.analyzer.issue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.client.api.common.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.client.api.common.TextEdit;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.plugin.api.issue.NewInputFileEdit;
import org.sonarsource.sonarlint.plugin.api.issue.NewTextEdit;

public class DefaultClientInputFileEdit implements ClientInputFileEdit, NewInputFileEdit {

  private final List<TextEdit> textEdits = new ArrayList<>();

  private ClientInputFile inputFile;

  @Override
  public NewInputFileEdit on(InputFile inputFile) {
    this.inputFile = ((SonarLintInputFile) inputFile).getClientInputFile();
    return this;
  }

  @Override
  public NewTextEdit newTextEdit() {
    return new DefaultTextEdit();
  }

  @Override
  public NewInputFileEdit addTextEdit(NewTextEdit newTextEdit) {
    textEdits.add((DefaultTextEdit) newTextEdit);
    return this;
  }

  @Override
  public ClientInputFile target() {
    return inputFile;
  }

  @Override
  public List<TextEdit> textEdits() {
    return Collections.unmodifiableList(textEdits);
  }
}
