/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.tracking;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.PreDestroy;
import org.sonarsource.sonarlint.core.UserPaths;

public class KnownFindingsStorageService {

  private final Path projectsStorageBaseDir;
  private final Path workDir;
  private final AtomicReference<XodusKnownFindingsStore> trackedIssuesStore = new AtomicReference<>();

  public KnownFindingsStorageService(UserPaths userPaths) {
    this.projectsStorageBaseDir = userPaths.getStorageRoot();
    this.workDir = userPaths.getWorkDir();
  }

  public synchronized XodusKnownFindingsStore get() {
    var store = trackedIssuesStore.get();
    if (store == null) {
      try {
        store = new XodusKnownFindingsStore(projectsStorageBaseDir, workDir);
        trackedIssuesStore.set(store);
        return store;
      } catch (IOException e) {
        throw new IllegalStateException("Unable to create tracked issues database", e);
      }
    }
    return store;
  }

  @PreDestroy
  public void close() {
    var store = trackedIssuesStore.get();
    if (store != null) {
      store.close();
    }
  }

}
