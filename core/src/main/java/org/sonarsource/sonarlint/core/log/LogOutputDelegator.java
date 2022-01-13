/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

public class LogOutputDelegator {
  private InheritableThreadLocal<LogOutput> target = new InheritableThreadLocal<>();

  public void log(String formattedMessage, Level level) {
    LogOutput output = target.get();
    if (output != null) {
      output.log(formattedMessage, level);
    }
  }

  public void log(String formattedMessage, Level level, Throwable t) {
    log(formattedMessage, level);

    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    log(sw.toString(), level);
  }

  public void setTarget(@Nullable LogOutput target) {
    this.target.set(target);
  }
}
