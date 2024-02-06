/*
 * SonarLint Core - ITs - Tests
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
package its.utils;

import java.util.Queue;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

public class LogOnTestFailure implements TestWatcher {

  private final Queue<LogParams> logs;

  public LogOnTestFailure(Queue<LogParams> logs) {
    this.logs = logs;
  }

  @Override
  public void testFailed(ExtensionContext context, Throwable cause) {
    System.out.println("Test failed: " + context.getDisplayName());
    System.out.println("Client RPC logs: ");
    logs.forEach(l -> System.out.println("  " + StringUtils.rightPad(l.getLevel().name(), 5) + " " + l.getMessage()));
  }
}
