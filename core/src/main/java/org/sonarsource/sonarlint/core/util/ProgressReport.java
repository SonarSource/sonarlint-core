/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.util;

import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class ProgressReport implements Runnable {

  private static final Logger LOG = Loggers.get(ProgressReport.class);
  private final long period;
  private Supplier<String> messageSupplier = () -> "";
  private final Thread thread;
  private String stopMessage = null;

  public ProgressReport(String threadName, long period) {
    this.period = period;
    thread = new Thread(this, threadName);
    thread.setDaemon(true);
  }

  @Override
  public void run() {
    while (!Thread.interrupted()) {
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
    thread.interrupt();
    try {
      thread.join();
    } catch (InterruptedException e) {
      // Ignore
    }
  }

  private static void log(String message) {
    synchronized (LOG) {
      LOG.info(message);
      LOG.notifyAll();
    }
  }

}
