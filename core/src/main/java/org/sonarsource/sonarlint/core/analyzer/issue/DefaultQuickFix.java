/*
 * SonarLint Core - Implementation
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
import java.util.List;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFileEdit;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.plugin.api.issue.NewInputFileEdit;
import org.sonarsource.sonarlint.plugin.api.issue.NewQuickFix;

public class DefaultQuickFix implements QuickFix, NewQuickFix {

  private final List<ClientInputFileEdit> inputFileEdits = new ArrayList<>();

  private String message;

  @Override
  public NewQuickFix message(String message) {
    this.message = message;
    return this;
  }

  @Override
  public NewInputFileEdit newInputFileEdit() {
    return new DefaultClientInputFileEdit();
  }

  @Override
  public NewQuickFix addInputFileEdit(NewInputFileEdit newInputFileEdit) {
    inputFileEdits.add((DefaultClientInputFileEdit) newInputFileEdit);
    return this;
  }

  @Override
  public String message() {
    return message;
  }

  @Override
  public List<ClientInputFileEdit> inputFileEdits() {
    return inputFileEdits;
  }
}
