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
package org.sonarsource.sonarlint.core.local.only;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.UserPaths;

import static org.sonarsource.sonarlint.core.commons.storage.XodusPurgeUtils.deleteInFolderWithPattern;
import static org.sonarsource.sonarlint.core.local.only.XodusLocalOnlyIssueStore.LOCAL_ONLY_ISSUE;

public class LocalOnlyIssueStorageService {

  private final Path projectsStorageBaseDir;
  private final Path workDir;
  private XodusLocalOnlyIssueStore localOnlyIssueStore;

  public LocalOnlyIssueStorageService(UserPaths userPaths) {
    this.projectsStorageBaseDir = userPaths.getStorageRoot();
    this.workDir = userPaths.getWorkDir();
  }

  @PostConstruct
  public void purgeOldIssues() {
    if (exists()) {
      get().purgeIssuesOlderThan(Instant.now().minus(7, ChronoUnit.DAYS));
    }
  }

  public boolean exists() {
    return Files.exists(projectsStorageBaseDir.resolve(XodusLocalOnlyIssueStore.BACKUP_TAR_GZ));
  }

  public XodusLocalOnlyIssueStore get() {
    if (localOnlyIssueStore == null) {
      try {
        localOnlyIssueStore = new XodusLocalOnlyIssueStore(projectsStorageBaseDir, workDir);
        return localOnlyIssueStore;
      } catch (IOException e) {
        throw new IllegalStateException("Unable to create local-only issue database", e);
      }
    }
    return localOnlyIssueStore;
  }

  @PreDestroy
  public void close() {
    if (localOnlyIssueStore != null) {
      localOnlyIssueStore.backupAndClose();
    }
  }

  public void delete() {
    if (localOnlyIssueStore != null) {
      localOnlyIssueStore.close();
      localOnlyIssueStore = null;
    }
    FileUtils.deleteQuietly(projectsStorageBaseDir.resolve(XodusLocalOnlyIssueStore.BACKUP_TAR_GZ).toFile());
    deleteInFolderWithPattern(workDir, LOCAL_ONLY_ISSUE + "*");
    deleteInFolderWithPattern(projectsStorageBaseDir, "local_only_issue_backup*");
  }
}
