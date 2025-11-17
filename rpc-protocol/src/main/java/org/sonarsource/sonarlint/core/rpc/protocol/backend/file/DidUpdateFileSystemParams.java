/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.file;

import java.net.URI;
import java.util.List;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;

public class DidUpdateFileSystemParams {

  private final List<ClientFileDto> addedFiles;
  private final List<ClientFileDto> changedFiles;
  private final List<URI> removedFiles;

  public DidUpdateFileSystemParams(List<ClientFileDto> addedFiles, List<ClientFileDto> changedFiles, List<URI> removedFiles) {
    this.addedFiles = addedFiles;
    this.changedFiles = changedFiles;
    this.removedFiles = removedFiles;
  }

  public List<ClientFileDto> getAddedFiles() {
    return addedFiles;
  }

  public List<ClientFileDto> getChangedFiles() {
    return changedFiles;
  }
  public List<URI> getRemovedFiles() {
    return removedFiles;
  }

}
