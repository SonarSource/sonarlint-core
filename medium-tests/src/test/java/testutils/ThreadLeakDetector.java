/*
 * SonarLint Core - Medium Tests
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

import java.util.Set;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadLeakDetector implements BeforeAllCallback, AfterAllCallback {
  private Set<Thread> beforeThreadSet;

  @Override
  public void beforeAll(ExtensionContext context) {
    this.beforeThreadSet = Thread.getAllStackTraces().keySet();
  }

  @Override
  public void afterAll(ExtensionContext context) {
    var afterThreadSet = Thread.getAllStackTraces().keySet();
    afterThreadSet.removeAll(beforeThreadSet);
    // There is no way to stop the Xodus threadJobProcessorPoolSpawner, but this is a deamon thread, so we can ignore it
    removeThread(afterThreadSet, "threadJobProcessorPoolSpawner");
    // This seems to be a JVM thread https://stackoverflow.com/questions/8224844/understanding-jvms-attach-listener-thread
    removeThread(afterThreadSet, "Attach Listener");
    assertThat(afterThreadSet).isEmpty();
  }

  private static void removeThread(Set<Thread> afterThreadSet, String name) {
    var xodusThreadJobProcessorPoolSpawner = afterThreadSet.stream()
      .filter(thread -> thread.getName().contains(name))
      .findFirst();
    xodusThreadJobProcessorPoolSpawner.ifPresent(afterThreadSet::remove);
  }
}
