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
package org.sonarsource.sonarlint.core.container.connected.update.objectstore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

public class PathGenerator implements Iterator<Path> {

  private final AtomicInteger counter = new AtomicInteger(0);

  private final int levels;
  private final int filesPerLevel;
  private final Path base;

  private final int limit;

  public PathGenerator(int levels, int filesPerLevel, Path base) {
    if (levels < 1) {
      throw new IllegalArgumentException("levels must be > 0");
    }
    if (filesPerLevel < 1) {
      throw new IllegalArgumentException("filesPerLevel must be > 0");
    }

    this.levels = levels;
    this.filesPerLevel = filesPerLevel;
    this.base = base;

    limit = (int) Math.pow(filesPerLevel, levels);
  }

  @Override
  public boolean hasNext() {
    return counter.get() < limit;
  }

  @Override
  public Path next() {
    if (!hasNext()) {
      throw new NoSuchElementException("path generator exhausted");
    }

    int count = counter.getAndIncrement();

    List<String> parts = new ArrayList<>(levels);
    for (int i = 0; i < levels; i++) {
      parts.add(Integer.toString(count % filesPerLevel));
      count /= filesPerLevel;
    }

    Collections.reverse(parts);

    Path path = base;
    for (String part : parts) {
      path = path.resolve(part);
    }

    return path;
  }
}
