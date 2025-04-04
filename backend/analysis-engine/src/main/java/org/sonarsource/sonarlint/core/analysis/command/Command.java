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
package org.sonarsource.sonarlint.core.analysis.command;

import java.util.concurrent.atomic.AtomicLong;
import org.sonarsource.sonarlint.core.analysis.container.global.ModuleRegistry;

public abstract class Command {
  private static final AtomicLong analysisGlobalNumber = new AtomicLong();
  private final long sequenceNumber = analysisGlobalNumber.incrementAndGet();

  public abstract void execute(ModuleRegistry moduleRegistry);

  public final long getSequenceNumber() {
    return sequenceNumber;
  }

  public boolean isReady() {
    return true;
  }

  public void cancel() {
    // most commands are not cancelable
  }

  public boolean shouldCancel(Command executingCommand) {
    return false;
  }
}
