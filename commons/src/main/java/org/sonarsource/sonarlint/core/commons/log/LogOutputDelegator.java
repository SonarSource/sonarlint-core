/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level;

class LogOutputDelegator {

  /**
   * In case we can't find a logger in the current Thread hierarchy, fallback on the first ClientLogOutput that has been passed
   */
  private static AtomicReference<ClientLogOutput> fallback = new AtomicReference<>();
  private final InheritableThreadLocal<ClientLogOutput> target = new InheritableThreadLocal<>();

  void log(String formattedMessage, Level level) {
    var output = Optional.ofNullable(target.get()).orElse(fallback.get());
    if (output != null) {
      output.log(formattedMessage, level);
    }
  }

  void log(@Nullable String formattedMessage, Level level, @Nullable Throwable t) {
    if (formattedMessage != null) {
      log(formattedMessage, level);
    }

    if (t != null) {
      var stringWriter = new StringWriter();
      var printWriter = new PrintWriter(stringWriter);
      t.printStackTrace(printWriter);
      log(stringWriter.toString(), level);
    }
  }

  void setTarget(@Nullable ClientLogOutput target) {
    fallback.compareAndSet(null, target);
    this.target.set(target);
  }
}
