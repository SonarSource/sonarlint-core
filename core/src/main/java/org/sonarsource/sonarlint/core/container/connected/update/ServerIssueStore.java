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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.container.connected.update.objectstore.HashingPathMapper;
import org.sonarsource.sonarlint.core.container.connected.update.objectstore.ObjectStore;
import org.sonarsource.sonarlint.core.container.connected.update.objectstore.Reader;
import org.sonarsource.sonarlint.core.container.connected.update.objectstore.SimpleObjectStore;
import org.sonarsource.sonarlint.core.container.connected.update.objectstore.Writer;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;

public class ServerIssueStore implements IssueStore {

  private static final Logger LOG = LoggerFactory.getLogger(ServerIssueStore.class);

  private final ObjectStore<String, List<ScannerInput.ServerIssue>> store;

  public ServerIssueStore(Path base) {
    HashingPathMapper pathGenerator = new HashingPathMapper(base, 2);

    Reader<List<ScannerInput.ServerIssue>> reader = input -> ProtobufUtil.readMessages(input, ScannerInput.ServerIssue.parser());

    Writer<List<ScannerInput.ServerIssue>> writer = ProtobufUtil::writeMessages;

    store = new SimpleObjectStore<>(pathGenerator, reader, writer);
  }

  @Override
  public void save(Map<String, List<ScannerInput.ServerIssue>> issues) {
    for (Map.Entry<String, List<ScannerInput.ServerIssue>> entry : issues.entrySet()) {
      String fileKey = entry.getKey();
      try {
        store.write(fileKey, entry.getValue());
      } catch (IOException e) {
        LOG.warn("failed to save issues for fileKey = " + fileKey, e);
      }
    }
  }

  @Override
  public List<ScannerInput.ServerIssue> load(String fileKey) {
    try {
      Optional<List<ScannerInput.ServerIssue>> issues = store.read(fileKey);
      if (issues.isPresent()) {
        return issues.get();
      }
    } catch (IOException e) {
      LOG.warn("failed to load issues for fileKey = " + fileKey, e);
    }
    return Collections.emptyList();
  }
}
