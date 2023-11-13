/*
<<<<<<<< HEAD:client/java-client-legacy/src/main/java/org/sonarsource/sonarlint/core/client/legacy/objectstore/Reader.java
 * SonarLint Core - Java Client Legacy
========
 * SonarLint Core - Implementation
>>>>>>>> 7fd22943b (SLCORE-625 Dispatch server events to services):core/src/main/java/org/sonarsource/sonarlint/core/event/SonarServerEventReceivedEvent.java
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
<<<<<<<< HEAD:client/java-client-legacy/src/main/java/org/sonarsource/sonarlint/core/client/legacy/objectstore/Reader.java
package org.sonarsource.sonarlint.core.client.legacy.objectstore;

import java.io.InputStream;
import java.util.function.Function;

@FunctionalInterface
public interface Reader<V> extends Function<InputStream, V> {
========
package org.sonarsource.sonarlint.core.event;

import org.sonarsource.sonarlint.core.commons.push.SonarServerEvent;

public class SonarServerEventReceivedEvent {
  private final String connectionId;
  private final SonarServerEvent event;

  public SonarServerEventReceivedEvent(String connectionId, SonarServerEvent event) {
    this.connectionId = connectionId;
    this.event = event;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public SonarServerEvent getEvent() {
    return event;
  }
>>>>>>>> 7fd22943b (SLCORE-625 Dispatch server events to services):core/src/main/java/org/sonarsource/sonarlint/core/event/SonarServerEventReceivedEvent.java
}
