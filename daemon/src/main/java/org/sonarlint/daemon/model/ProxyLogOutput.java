/*
 * SonarLint Daemon
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarlint.daemon.model;

import javax.annotation.Nullable;

import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.daemon.proto.SonarlintDaemon.LogEvent;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

public class ProxyLogOutput implements LogOutput {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ProxyLogOutput.class);
  private StreamObserver<LogEvent> response;

  public void setObserver(@Nullable StreamObserver<LogEvent> response) {
    if (this.response != null) {
      this.response.onCompleted();
    }
    this.response = response;
  }

  @Override
  public synchronized void log(String formattedMessage, Level level) {
    if (level == Level.ERROR) {
      LOGGER.error(formattedMessage);
    }

    if (response != null) {
      LogEvent log = LogEvent.newBuilder()
        .setLevel(level.name())
        .setLog(formattedMessage)
        .setIsDebug(level == Level.DEBUG || level == Level.TRACE)
        .build();
      try {
        response.onNext(log);
      } catch (StatusRuntimeException e) {
        LOGGER.info("Log stream closed: {}", e.getMessage());
        response = null;
      }
    }
  }
}
