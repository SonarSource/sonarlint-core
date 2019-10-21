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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.apache.commons.lang.SystemUtils;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.log.LogTester;

import static java.net.URI.create;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.sonarlint.languageserver.folders.WorkspaceFoldersManager.isAncestor;

public class WorkspaceFoldersManagerTest {

  @Rule
  public LogTester logTester = new LogTester();

  private WorkspaceFoldersManager underTest = new WorkspaceFoldersManager();

  @Test
  public void findFolderForFile_returns_correct_folder_when_exists() {
    Path basedir = Paths.get("path/to/base").toAbsolutePath();
    Path file = basedir.resolve("some/sub/file.java");

    WorkspaceFolder workspaceFolder = mockWorkspaceFolder(basedir.toUri());
    underTest.initialize(asList(
      mockWorkspaceFolder(Paths.get("other/path").toAbsolutePath().toUri()),
      workspaceFolder,
      mockWorkspaceFolder(Paths.get("other/path2").toAbsolutePath().toUri())));

    Optional<WorkspaceFolderWrapper> findFolderForFile = underTest.findFolderForFile(file.toUri());
    assertThat(findFolderForFile).isPresent();
    assertThat(findFolderForFile.get().getRootPath()).isEqualTo(basedir);
  }

  @Test
  public void findFolderForFile_returns_empty_when_no_folder_matched() {
    Path basedir = Paths.get("path/to/base").toAbsolutePath();
    Path file = basedir.resolve("some/sub/file.java");
    Path workspaceRoot = Paths.get("other/path");

    WorkspaceFolder workspaceFolder = mockWorkspaceFolder(workspaceRoot.toUri());
    underTest.initialize(Arrays.asList(workspaceFolder,
      mockWorkspaceFolder(Paths.get("other/path2").toAbsolutePath().toUri())));

    assertThat(underTest.findFolderForFile(file.toUri())).isEmpty();
  }

  @Test
  public void findBaseDir_finds_deepest_nested_folder() {
    Path basedir = Paths.get("path/to/base").toAbsolutePath();
    Path subFolder = basedir.resolve("sub");
    URI file = subFolder.resolve("file.java").toUri();

    underTest.initialize(asList(
      mockWorkspaceFolder(subFolder.toUri()),
      mockWorkspaceFolder(basedir.toUri())));

    Optional<WorkspaceFolderWrapper> findFolderForFile = underTest.findFolderForFile(file);
    assertThat(findFolderForFile).isPresent();
    assertThat(findFolderForFile.get().getRootPath()).isEqualTo(subFolder);
  }

  @Test
  public void initialize_does_not_crash_when_no_folders() {
    underTest.initialize(null);

    assertThat(underTest.getAll()).isEmpty();
  }

  @Test
  public void fail_if_initialize_called_twice() {
    underTest.initialize(null);

    try {
      underTest.initialize(null);
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  public void fail_if_not_initialized() {
    try {
      underTest.getAll();
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  public void testURIAncestor() {
    assertThat(isAncestor(create("file:///foo"), create("file:///foo"))).isTrue();
    assertThat(isAncestor(create("file:///foo"), create("file:///foo/bar.txt"))).isTrue();
    assertThat(isAncestor(create("file:///foo/bar"), create("file:///foo/bar.txt"))).isFalse();
    assertThat(isAncestor(create("file:///foo/bar"), create("file:///foo/bar2"))).isFalse();

    // Non file scheme
    assertThat(isAncestor(create("ftp://ftp.example.com/foo"), create("ftp://ftp.example.com/foo"))).isTrue();
    assertThat(isAncestor(create("ftp://ftp.example.com/foo"), create("ftp://ftp.example.com/foo/bar.txt"))).isTrue();
    assertThat(isAncestor(create("ftp://ftp.example.com/foo/bar"), create("ftp://ftp.example.com/foo/bar.txt"))).isFalse();
    assertThat(isAncestor(create("ftp://ftp.example.com/foo/bar"), create("ftp://ftp.example.com/foo/bar2"))).isFalse();
    assertThat(isAncestor(create("ftp://ftp.example.com/foo/bar"), create("ftp://ftp.example.com/bar.txt"))).isFalse();

    // Windows
    assertThat(isAncestor(create("file:///C:/Documents%20and%20Settings/davris"), create("file:///C:/Documents%20and%20Settings/davris/FileSchemeURIs.doc"))).isTrue();

    // Corner cases
    // Not the same scheme
    assertThat(isAncestor(create("ftp:///foo"), create("file:///foo"))).isFalse();
    // Not the same host
    assertThat(isAncestor(create("file://laptop/My%20Documents"), create("file://laptop2/My%20Documents/FileSchemeURIs.doc"))).isFalse();
    // Not the same port
    assertThat(isAncestor(create("file://laptop:8080/My%20Documents"), create("file://laptop:8081/My%20Documents/FileSchemeURIs.doc"))).isFalse();
    // Opaque
    try {
      isAncestor(create("mailto:a@b.com"), create("file:///foo"));
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
    try {
      isAncestor(create("file:///foo"), create("mailto:a@b.com"));
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  public void testURIAncestor_UNC_path() {
    // Fail on Linux with IllegalArgumentException: URI has an authority component
    assumeTrue(SystemUtils.IS_OS_WINDOWS);

    assertThat(isAncestor(create("file://laptop/My%20Documents"), create("file://laptop/My%20Documents/FileSchemeURIs.doc"))).isTrue();
  }

  @Test
  public void register_new_folder() {
    underTest.initialize(Collections.emptyList());
    assertThat(underTest.getAll()).isEmpty();

    Path basedir = Paths.get("path/to/base").toAbsolutePath();
    WorkspaceFolder workspaceFolder = mockWorkspaceFolder(basedir.toUri());

    underTest.didChangeWorkspaceFolders(new WorkspaceFoldersChangeEvent(asList(workspaceFolder), Collections.emptyList()));

    assertThat(underTest.getAll()).extracting(WorkspaceFolderWrapper::getRootPath).containsExactly(basedir);
    assertThat(logTester.logs()).isEmpty();

    // Should never occurs
    underTest.didChangeWorkspaceFolders(new WorkspaceFoldersChangeEvent(asList(workspaceFolder), Collections.emptyList()));

    assertThat(underTest.getAll()).extracting(WorkspaceFolderWrapper::getRootPath).containsExactly(basedir);
    assertThat(logTester.logs()).containsExactly("Registered workspace folder was already added: WorkspaceFolder [\n" +
      "  uri = \"" + basedir.toUri() + "\"\n" +
      "  name = null\n" +
      "]");
  }

  @Test
  public void unregister_folder() {
    Path basedir = Paths.get("path/to/base").toAbsolutePath();
    WorkspaceFolder workspaceFolder = mockWorkspaceFolder(basedir.toUri());

    underTest.initialize(asList(workspaceFolder));
    assertThat(underTest.getAll()).extracting(WorkspaceFolderWrapper::getRootPath).containsExactly(basedir);

    underTest.didChangeWorkspaceFolders(new WorkspaceFoldersChangeEvent(Collections.emptyList(), asList(workspaceFolder)));

    assertThat(underTest.getAll()).isEmpty();
    assertThat(logTester.logs()).isEmpty();

    // Should never occurs
    underTest.didChangeWorkspaceFolders(new WorkspaceFoldersChangeEvent(Collections.emptyList(), asList(workspaceFolder)));

    assertThat(underTest.getAll()).isEmpty();
    assertThat(logTester.logs()).containsExactly("Unregistered workspace folder was missing: WorkspaceFolder [\n" +
      "  uri = \"" + basedir.toUri() + "\"\n" +
      "  name = null\n" +
      "]");
  }

  private static WorkspaceFolder mockWorkspaceFolder(URI uri) {
    return new WorkspaceFolder(uri.toString());
  }
}
