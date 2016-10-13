/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonar.scanner.protocol.input.ScannerInput.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.connected.objectstore.HashingPathMapper;
import org.sonarsource.sonarlint.core.container.connected.objectstore.ObjectStore;
import org.sonarsource.sonarlint.core.container.connected.objectstore.Reader;
import org.sonarsource.sonarlint.core.container.connected.objectstore.SimpleObjectStore;
import org.sonarsource.sonarlint.core.container.connected.objectstore.Writer;
import org.sonarsource.sonarlint.core.container.connected.update.IssueUtils;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;

public class ServerIssueStore implements IssueStore {
  private final ObjectStore<String, Iterator<ScannerInput.ServerIssue>> store;

  public ServerIssueStore(Path base) {
    HashingPathMapper pathGenerator = new HashingPathMapper(base, 2);

    Reader<Iterator<ScannerInput.ServerIssue>> reader = input -> ProtobufUtil.streamMessages(input, ScannerInput.ServerIssue.parser());

    Writer<Iterator<ScannerInput.ServerIssue>> writer = ProtobufUtil::writeMessages;

    store = new SimpleObjectStore<>(pathGenerator, reader, writer);
  }

  @Override
  public void save(Iterator<ScannerInput.ServerIssue> issues) {
    Spliterator<ScannerInput.ServerIssue> spliterator = Spliterators.spliteratorUnknownSize(issues, 0);
    Map<String, List<ServerIssue>> issuesPerFile = StreamSupport.stream(spliterator, false).collect(Collectors.groupingBy(IssueUtils::createFileKey));

    for (Map.Entry<String, List<ServerIssue>> entry : issuesPerFile.entrySet()) {
      try {
        store.write(entry.getKey(), entry.getValue().iterator());
      } catch (IOException e) {
        throw new StorageException("failed to save issues for fileKey = " + entry.getKey(), e);
      }
    }
  }

  @Override
  public Iterator<ScannerInput.ServerIssue> load(String fileKey) {
    try {
      Optional<Iterator<ScannerInput.ServerIssue>> issues = store.read(fileKey);
      if (issues.isPresent()) {
        return issues.get();
      }
    } catch (IOException e) {
      throw new StorageException("failed to load issues for fileKey = " + fileKey, e);
    }
    return Collections.emptyIterator();
  }
}
