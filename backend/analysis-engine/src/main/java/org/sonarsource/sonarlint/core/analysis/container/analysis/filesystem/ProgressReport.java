/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class ProgressReport implements Runnable {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final long period;
  private Supplier<String> messageSupplier = () -> "";
  private final Thread thread;
  private String stopMessage = null;
  private volatile boolean stop = false;

  public ProgressReport(String threadName, long period) {
    this.period = period;
    thread = new Thread(this, threadName);
    thread.setDaemon(true);
  }

  @Override
  public void run() {
    while (!stop) {
      try {
        Thread.sleep(period);
        log(messageSupplier.get());
      } catch (InterruptedException e) {
        break;
      }
    }
    if (stopMessage != null) {
      log(stopMessage);
    }
  }

  public void start(String startMessage) {
    log(startMessage);
    thread.start();
  }

  public void message(Supplier<String> messageSupplier) {
    this.messageSupplier = messageSupplier;
  }

  public void stop(@Nullable String stopMessage) {
    this.stopMessage = stopMessage;
    this.stop = true;
    thread.interrupt();
    try {
      thread.join(1000);
    } catch (InterruptedException e) {
      // Ignore
    }
  }

  private static void log(String message) {
    LOG.info(message);
  }

}
