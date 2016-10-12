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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.scanner.protocol.input.ScannerInput.ServerIssue;
import org.sonarsource.sonarlint.core.container.connected.ServerIssueStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ServerIssueStoreTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void should_read_object_written() {
    ServerIssueStore store = new ServerIssueStore(temporaryFolder.getRoot().toPath());

    ServerIssue.Builder builder = ServerIssue.newBuilder();
    List<ServerIssue> issueList = new ArrayList<>();

    String moduleKey = "module1";
    String key1 = "path1";
    String key2 = "path2";

    issueList.add(builder.setModuleKey(moduleKey).setPath(key1).build());
    issueList.add(builder.setModuleKey(moduleKey).setPath(key2).build());

    store.save(issueList.iterator());

    assertThat(store.load("module1:path1")).containsExactly(issueList.get(0));
    assertThat(store.load("module1:path2")).containsExactly(issueList.get(1));
    assertThat(store.load("nonexistent")).isEmpty();
  }

  @Test
  public void should_read_object_replaced() throws IOException {
    ServerIssueStore store = new ServerIssueStore(temporaryFolder.getRoot().toPath());

    String key = "someKey";
    ScannerInput.ServerIssue issue1 = ScannerInput.ServerIssue.newBuilder().setLine(11).build();
    ScannerInput.ServerIssue issue2 = ScannerInput.ServerIssue.newBuilder().setLine(22).build();

    store.save(Collections.singletonMap(key, Collections.singletonList(issue1)));
    assertThat(store.load(key)).containsOnly(issue1);

    store.save(Collections.singletonMap(key, Collections.singletonList(issue2)));
    assertThat(store.load(key)).containsOnly(issue2);
  }

  @Test
  public void should_fail_to_save_object_if_cannot_write_to_filesystem() throws IOException {
    File forbiddenDir = temporaryFolder.newFolder();
    if (!forbiddenDir.setReadOnly()) {
      fail("could not make dir readonly");
    }

    String key = "someKey";

    ServerIssueStore store = new ServerIssueStore(forbiddenDir.toPath());
    store.save(Collections.singletonMap(key, Collections.singletonList(ScannerInput.ServerIssue.getDefaultInstance())));

    assertThat(store.load(key)).isEmpty();
  }
}
