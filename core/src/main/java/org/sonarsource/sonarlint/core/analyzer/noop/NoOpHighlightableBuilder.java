/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.analyzer.noop;

import javax.annotation.CheckForNull;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.source.Highlightable;
import org.sonarsource.sonarlint.core.analyzer.perspectives.PerspectiveBuilder;

public class NoOpHighlightableBuilder extends PerspectiveBuilder<Highlightable> {

  private static final NoOpHighlightable NO_OP_HIGHLIGHTABLE = new NoOpHighlightable();

  public NoOpHighlightableBuilder() {
    super(Highlightable.class);
  }

  @CheckForNull
  @Override
  public Highlightable loadPerspective(Class<Highlightable> perspectiveClass, InputComponent component) {
    return NO_OP_HIGHLIGHTABLE;
  }
}
