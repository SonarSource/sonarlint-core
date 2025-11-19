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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.PreDestroy;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.UserPaths;

import static org.sonarsource.sonarlint.core.commons.storage.XodusPurgeUtils.deleteInFolderWithPattern;
import static org.sonarsource.sonarlint.core.tracking.XodusKnownFindingsStore.BACKUP_TAR_GZ;
import static org.sonarsource.sonarlint.core.tracking.XodusKnownFindingsStore.KNOWN_FINDINGS_STORE;

public class XodusKnownFindingsStorageService {

  private final Path projectsStorageBaseDir;
  private final Path workDir;
  private final AtomicReference<XodusKnownFindingsStore> trackedIssuesStore = new AtomicReference<>();

  public XodusKnownFindingsStorageService(UserPaths userPaths) {
    this.projectsStorageBaseDir = userPaths.getStorageRoot();
    this.workDir = userPaths.getWorkDir();
  }

  public boolean exists() {
    return Files.exists(projectsStorageBaseDir.resolve(XodusKnownFindingsStore.BACKUP_TAR_GZ));
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

  public void delete() {
    var store = trackedIssuesStore.getAndSet(null);
    if (store != null) {
      store.close();
    }
    FileUtils.deleteQuietly(projectsStorageBaseDir.resolve(BACKUP_TAR_GZ).toFile());
    deleteInFolderWithPattern(workDir, KNOWN_FINDINGS_STORE + "*");
    deleteInFolderWithPattern(projectsStorageBaseDir, "known_findings_backup*");
  }
}
