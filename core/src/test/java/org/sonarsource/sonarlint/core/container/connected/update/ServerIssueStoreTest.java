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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.scanner.protocol.input.ScannerInput;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerIssueStoreTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void should_read_object_written() {
    ServerIssueStore store = new ServerIssueStore(temporaryFolder.getRoot().toPath());

    Map<String, List<ScannerInput.ServerIssue>> map = new HashMap<>();

    ScannerInput.ServerIssue.Builder builder = ScannerInput.ServerIssue.newBuilder();

    String key1 = "someKey1";
    List<ScannerInput.ServerIssue> issueList1 = Collections.singletonList(builder.setKey(key1).build());
    map.put(key1, issueList1);

    String key2 = "someKey2";
    List<ScannerInput.ServerIssue> issueList2 = Collections.singletonList(builder.setKey(key2).build());
    map.put(key2, issueList2);

    store.save(map);

    assertThat(store.load(key1)).isEqualTo(issueList1);
    assertThat(store.load(key2)).isEqualTo(issueList2);
    assertThat(store.load("nonexistent")).isEmpty();
  }
}
