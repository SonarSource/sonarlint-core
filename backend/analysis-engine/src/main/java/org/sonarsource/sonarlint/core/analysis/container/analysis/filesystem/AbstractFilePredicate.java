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
package org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem;

import java.util.stream.StreamSupport;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FileSystem.Index;
import org.sonar.api.batch.fs.InputFile;

/**
 * Partial implementation of {@link FilePredicate}.
 * @since 5.1
 */
public abstract class AbstractFilePredicate implements OptimizedFilePredicate {

  protected static final int DEFAULT_PRIORITY = 10;

  @Override
  public Iterable<InputFile> filter(Iterable<InputFile> target) {
    return StreamSupport.stream(target.spliterator(), false).filter(AbstractFilePredicate.this::apply).toList();
  }

  @Override
  public Iterable<InputFile> get(Index index) {
    return filter(index.inputFiles());
  }

  @Override
  public int priority() {
    return DEFAULT_PRIORITY;
  }

  @Override
  public final int compareTo(OptimizedFilePredicate o) {
    return o.priority() - priority();
  }

}
