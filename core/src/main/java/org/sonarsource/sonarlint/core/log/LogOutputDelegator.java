/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput.Level;
import org.sonarsource.sonarlint.core.util.LoggedErrorHandler;

public class LogOutputDelegator {
  private InheritableThreadLocal<LogOutput> target = new InheritableThreadLocal<>();
  private InheritableThreadLocal<LoggedErrorHandler> errorHandler = new InheritableThreadLocal<>();

  public void log(String formattedMessage, Level level) {
    LogOutput output = target.get();
    if (output != null) {
      output.log(formattedMessage, level);
    }

    if (level == Level.ERROR) {
      LoggedErrorHandler h = errorHandler.get();
      if (h != null) {
        h.handleError(formattedMessage);
      }
    }
  }

  public void logError(String msg, Throwable t) {
    LoggedErrorHandler h = errorHandler.get();
    if (h != null) {
      h.handleException(t.getClass().getName());
      h.handleError(msg);
    }

    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);

    LogOutput output = target.get();
    if (output != null) {
      output.log(msg, Level.ERROR);
    }
  }

  public void setTarget(@Nullable LogOutput target) {
    this.target.set(target);
  }

  public void setErrorHandler(@Nullable LoggedErrorHandler errorHandler) {
    this.errorHandler.set(errorHandler);
  }

}
