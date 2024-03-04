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
package org.sonarsource.sonarlint.core.analysis.container.analysis.issue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.issue.fix.InputFileEdit;
import org.sonar.api.batch.sensor.issue.fix.NewInputFileEdit;
import org.sonar.api.batch.sensor.issue.fix.NewTextEdit;
import org.sonar.api.batch.sensor.issue.fix.TextEdit;

public class SensorInputFileEdit implements InputFileEdit, NewInputFileEdit, org.sonarsource.sonarlint.plugin.api.issue.NewInputFileEdit {

  private final List<TextEdit> textEdits = new ArrayList<>();

  private InputFile inputFile;

  @Override
  public SensorInputFileEdit on(InputFile inputFile) {
    this.inputFile = inputFile;
    return this;
  }

  @Override
  public SensorTextEdit newTextEdit() {
    return new SensorTextEdit();
  }

  @Override
  public NewInputFileEdit addTextEdit(NewTextEdit newTextEdit) {
    textEdits.add((SensorTextEdit) newTextEdit);
    return this;
  }

  @Override
  public org.sonarsource.sonarlint.plugin.api.issue.NewInputFileEdit addTextEdit(org.sonarsource.sonarlint.plugin.api.issue.NewTextEdit newTextEdit) {
    // legacy method from sonarlint-plugin-api, keep for backward compatibility and remove later
    textEdits.add((SensorTextEdit) newTextEdit);
    return this;
  }

  @Override
  public InputFile target() {
    return inputFile;
  }

  @Override
  public List<TextEdit> textEdits() {
    return Collections.unmodifiableList(textEdits);
  }
}
