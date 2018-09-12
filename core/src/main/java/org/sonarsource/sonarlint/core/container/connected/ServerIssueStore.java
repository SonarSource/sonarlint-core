/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.HashingPathMapper;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.ObjectStore;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Reader;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Writer;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.connected.objectstore.SimpleObjectStore;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;

public class ServerIssueStore implements IssueStore {
  private final ObjectStore<String, List<ServerIssue>> store;

  public ServerIssueStore(Path base) {
    HashingPathMapper pathGenerator = new HashingPathMapper(base, 2);

    Reader<List<ServerIssue>> reader = input -> ProtobufUtil.readMessages(input, ServerIssue.parser());

    Writer<List<ServerIssue>> writer = ProtobufUtil::writeMessages;

    store = new SimpleObjectStore<>(pathGenerator, reader, writer);
  }

  @Override
  public synchronized void save(List<ServerIssue> issues) {
    // organize everything in memory to avoid making an IO access per issue
    Map<String, List<ServerIssue>> issuesPerFile = issues.stream().collect(Collectors.groupingBy(ServerIssue::getPath));

    for (Map.Entry<String, List<ServerIssue>> entry : issuesPerFile.entrySet()) {
      try {
        store.write(entry.getKey(), entry.getValue());
      } catch (IOException e) {
        throw new StorageException("failed to save issues for fileKey = " + entry.getKey(), e);
      }
    }
  }

  @Override
  public synchronized void delete(String fileKey) {
    try {
      store.delete(fileKey);
    } catch (IOException e) {
      throw new StorageException("failed to delete issues for fileKey = " + fileKey, e);
    }
  }

  @Override
  public synchronized List<ServerIssue> load(String fileKey) {
    try {
      Optional<List<ServerIssue>> issues = store.read(fileKey);
      return issues.orElse(Collections.emptyList());
    } catch (IOException e) {
      throw new StorageException("failed to load issues for fileKey = " + fileKey, e);
    }
  }
}
