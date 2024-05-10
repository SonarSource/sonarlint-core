/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.tracking;

import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.PreDestroy;
import javax.inject.Named;

public class KnownFindingsStorageService {

  private final Path projectsStorageBaseDir;
  private final Path workDir;
  private XodusKnownFindingsStore trackedIssuesStore;

  public KnownFindingsStorageService(@Named("storageRoot") Path storageRoot, @Named("userHome") Path workDir) {
    projectsStorageBaseDir = storageRoot;
    this.workDir = workDir;
  }

  public XodusKnownFindingsStore get() {
    if (trackedIssuesStore == null) {
      try {
        trackedIssuesStore = new XodusKnownFindingsStore(projectsStorageBaseDir, workDir);
        return trackedIssuesStore;
      } catch (IOException e) {
        throw new IllegalStateException("Unable to create tracked issues database", e);
      }
    }
    return trackedIssuesStore;
  }

  @PreDestroy
  public void close() {
    if (trackedIssuesStore != null) {
      trackedIssuesStore.close();
    }
  }

}
