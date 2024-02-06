/*
 * SonarLint Core - Commons
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.LogOutput.Level;

class LogOutputDelegator {

  /**
   * Temporary until the actual log is removed from sonar-analyzer-commons
   * See SLCORE-520
   */
  private static final Pattern SKIPPED_MESSAGE_PATTERN = Pattern.compile("^Skipping section '.*?' for rule '.*?', content is empty$");

  private final InheritableThreadLocal<LogOutput> target = new InheritableThreadLocal<>();

  void log(@Nullable String formattedMessage, Level level, @Nullable String stackTrace) {
    var output = Optional.ofNullable(target.get()).orElseThrow(() -> {
      var noLogOutputConfigured = new IllegalStateException("No log output configured");
      noLogOutputConfigured.printStackTrace(System.err);
      return noLogOutputConfigured;
    });
    if (output != null) {
      if (formattedMessage != null && level == Level.DEBUG && SKIPPED_MESSAGE_PATTERN.matcher(formattedMessage).matches()) {
        return;
      }
      output.log(formattedMessage, level, stackTrace);
    }
  }

  void log(@Nullable String formattedMessage, Level level, @Nullable Throwable t) {
    String stacktrace = null;
    if (t != null) {
      stacktrace = LogOutput.stackTraceToString(t);
    }
    if (formattedMessage != null || t != null) {
      log(formattedMessage, level, stacktrace);
    }
  }

  void setTarget(@Nullable LogOutput target) {
    this.target.set(target);
  }

  @CheckForNull
  public LogOutput getTarget() {
    return this.target.get();
  }

}
