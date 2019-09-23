/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarlint.languageserver.folders;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import static java.net.URI.create;

public class WorkspaceFoldersManager {

  private static final Logger LOG = Loggers.get(WorkspaceFoldersManager.class);

  private final Map<URI, WorkspaceFolderWrapper> folders = new HashMap<>();

  private boolean initialized;

  public void initialize(@Nullable List<WorkspaceFolder> workspaceFolders) {
    if (initialized) {
      throw new IllegalStateException("WorkspaceFoldersManager has already been initialized");
    }
    if (workspaceFolders != null) {
      workspaceFolders.forEach(wf -> {
        URI uri = create(wf.getUri());
        folders.put(uri, new WorkspaceFolderWrapper(uri, wf));
      });
    }
    this.initialized = true;
  }

  public void didChangeWorkspaceFolders(WorkspaceFoldersChangeEvent event) {
    checkInitialized();
    for (WorkspaceFolder removed : event.getRemoved()) {
      URI uri = create(removed.getUri());
      if (!folders.containsKey(uri)) {
        LOG.warn("Unregistered workspace folder was missing: " + removed);
        continue;
      }
      folders.remove(uri);
    }
    for (WorkspaceFolder added : event.getAdded()) {
      URI uri = create(added.getUri());
      if (folders.containsKey(uri)) {
        LOG.warn("Registered workspace folder was already added: " + added);
        continue;
      }
      folders.put(uri, new WorkspaceFolderWrapper(uri, added));
    }

  }

  public Optional<WorkspaceFolderWrapper> findFolderForFile(URI uri) {
    checkInitialized();
    List<URI> folderUriCandidates = folders.keySet().stream()
      .filter(wfRoot -> isAncestor(wfRoot, uri))
      // Sort by path descending length to prefer the deepest one in case of multiple nested workspace folders
      .sorted(Comparator.<URI>comparingInt(wfRoot -> wfRoot.getPath().length()).reversed())
      .collect(Collectors.toList());
    if (folderUriCandidates.isEmpty()) {
      return Optional.empty();
    }
    if (folderUriCandidates.size() > 1) {
      LOG.debug("Multiple candidates workspace folders to contains {}. Default to the deepest one.", uri);
    }
    return Optional.of(folders.get(folderUriCandidates.get(0)));
  }

  // Visible for testing
  static boolean isAncestor(URI folderUri, URI fileUri) {
    if (folderUri.isOpaque() || fileUri.isOpaque()) {
      throw new IllegalArgumentException("Only hierarchical URIs are supported");
    }
    if (!folderUri.getScheme().equalsIgnoreCase(fileUri.getScheme())) {
      return false;
    }
    if (!Objects.equals(folderUri.getHost(), fileUri.getHost())) {
      return false;
    }
    if (folderUri.getPort() != fileUri.getPort()) {
      return false;
    }
    return Paths.get(fileUri.getPath()).startsWith(Paths.get(folderUri.getPath()));
  }

  public Collection<WorkspaceFolderWrapper> getAll() {
    checkInitialized();
    return folders.values();
  }

  private void checkInitialized() {
    if (!initialized) {
      throw new IllegalStateException("WorkspaceFoldersManager has not been initialized");
    }
  }

}
