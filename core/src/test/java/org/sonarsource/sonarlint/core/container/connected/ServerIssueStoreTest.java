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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerIssue;

public class ServerIssueStoreTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

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

    store.save(issueList);

    assertThat(store.load("path1")).containsExactly(issueList.get(0));
    assertThat(store.load("path2")).containsExactly(issueList.get(1));
    assertThat(store.load("nonexistent")).isEmpty();
  }

  @Test
  public void should_read_object_replaced() throws IOException {
    ServerIssueStore store = new ServerIssueStore(temporaryFolder.getRoot().toPath());

    String path = "myfile";
    String moduleKey = "module";

    ServerIssue issue1 = ServerIssue.newBuilder().setPath(path).setModuleKey(moduleKey).setLine(11).build();
    ServerIssue issue2 = ServerIssue.newBuilder().setPath(path).setModuleKey(moduleKey).setLine(22).build();

    store.save(Collections.singletonList(issue1));
    assertThat(store.load("myfile")).containsOnly(issue1);

    store.save(Collections.singletonList(issue2));
    assertThat(store.load("myfile")).containsOnly(issue2);
  }

  @Test
  public void should_fail_to_save_object_if_cannot_write_to_filesystem() throws IOException {
    Path root = temporaryFolder.newFolder().toPath();

    String moduleKey = "module1";
    String path = "path1";
    // the sha1sum of path
    String sha1sum = "074aeb9c5551d3b52d26cf3d6568599adbff99f1";

    // Create a directory at the path where ServerIssueStore would want to create a file,
    // in order to obstruct the file creation
    Path wouldBeFile = root.resolve("0").resolve("7").resolve(sha1sum);
    if (!wouldBeFile.toFile().mkdirs()) {
      fail("could not create dummy directory");
    }

    ServerIssueStore store = new ServerIssueStore(root);

    exception.expect(StorageException.class);
    store.save(Collections.singletonList(ServerIssue.newBuilder().setModuleKey(moduleKey).setPath(path).build()));
  }

  @Test
  public void should_fail_to_delete_object() throws IOException {
    Path root = temporaryFolder.newFolder().toPath();
    ServerIssueStore store = new ServerIssueStore(root);

    String fileKey = "module1:path1";
    // the sha1sum of fileKey
    String sha1sum = "18054aada7bd3b7ddd6de55caf50ae7bee376430";

    // Create a dummy sub-directory at the path where ServerIssueStore would want to delete a file,
    // in order to obstruct the file deletion
    Path dummySubDir = root.resolve("1").resolve("8").resolve(sha1sum).resolve("dummysub");
    if (!dummySubDir.toFile().mkdirs()) {
      fail("could not create dummy sub-directory");
    }
    exception.expect(StorageException.class);
    store.delete(fileKey);
  }

  @Test
  public void should_delete_entries() {
    ServerIssueStore store = new ServerIssueStore(temporaryFolder.getRoot().toPath());

    ServerIssue.Builder builder = ServerIssue.newBuilder();
    List<ServerIssue> issueList = new ArrayList<>();

    String moduleKey = "module1";
    String key1 = "path1";
    String key2 = "path2";

    issueList.add(builder.setPath(key1).setModuleKey(moduleKey).build());
    issueList.add(builder.setPath(key2).setModuleKey(moduleKey).build());

    store.save(issueList);
    store.delete("path1");
    store.delete("path2");
    store.delete("non_existing");

    assertThat(store.load("path1")).isEmpty();
    assertThat(store.load("path2")).isEmpty();
  }
}
