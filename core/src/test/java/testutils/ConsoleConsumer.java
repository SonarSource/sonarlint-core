/*
 * SonarLint Core - Implementation
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
package testutils;

import java.io.IOException;

// taken from org.jboss.as.arquillian.container.managed.ManagedDeployableContainer
public class ConsoleConsumer implements Runnable {

    private final Process process;

    public ConsoleConsumer(Process process) {
        this.process = process;
    }

    @Override
  public void run() {
    final var stream = process.getInputStream();

    try {
      byte[] buf = new byte[32];
      int num;
      // Do not try reading a line cos it considers '\r' end of line
      while ((num = stream.read(buf)) != -1) {
        System.out.write(buf, 0, num);
      }
    } catch (IOException e) {
    }
  }

}
