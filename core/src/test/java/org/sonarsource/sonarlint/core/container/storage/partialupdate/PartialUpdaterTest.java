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
package org.sonarsource.sonarlint.core.container.storage.partialupdate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sonar.scanner.protocol.input.ScannerInput.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.container.connected.IssueStore;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleListDownloader;
import org.sonarsource.sonarlint.core.container.storage.IssueStoreReader;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;

public class PartialUpdaterTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Mock
  private IssueStoreFactory issueStoreFactory;
  @Mock
  private IssueDownloader downloader;
  @Mock
  private StorageManager storageManager;
  @Mock
  private IssueStoreReader issueStoreReader;
  @Mock
  private IssueStore issueStore;
  @Mock
  private ModuleListDownloader moduleListDownloader;

  private PartialUpdater updater;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    updater = new PartialUpdater(issueStoreFactory, downloader, storageManager, issueStoreReader, moduleListDownloader);
    when(issueStoreFactory.apply(Mockito.any(Path.class))).thenReturn(issueStore);
  }

  @Test
  public void update_file_issues() {
    ServerIssue issue = ServerIssue.newBuilder().setKey("issue1").build();
    Iterator<ServerIssue> issues = Collections.singletonList(issue).iterator();

    when(storageManager.getServerIssuesPath("module")).thenReturn(temp.getRoot().toPath());
    when(issueStoreReader.getFileKey("module", "file")).thenReturn("module:file");
    when(downloader.apply("module:file")).thenReturn(issues);

    updater.updateFileIssues("module", "file");

    verify(issueStore).save(issues);
  }

  @Test
  public void error_downloading_issues() {
    when(storageManager.getServerIssuesPath("module")).thenReturn(temp.getRoot().toPath());
    when(issueStoreReader.getFileKey("module", "file")).thenReturn("module:file");
    when(downloader.apply("module:file")).thenThrow(IOException.class);

    exception.expect(DownloadException.class);
    updater.updateFileIssues("module", "file");
  }

  @Test
  public void error_downloading_modules() {
    when(storageManager.getModuleListPath()).thenReturn(temp.getRoot().toPath());
    doThrow(IOException.class).when(moduleListDownloader).fetchModulesList(temp.getRoot().toPath());
    exception.expect(DownloadException.class);

    updater.updateModuleList();
  }

  @Test
  public void create() {
    ServerConfiguration serverConfiguration = mock(ServerConfiguration.class);
    when(serverConfiguration.getUrl()).thenReturn("http://fake.com");
    assertThat(PartialUpdater.create(storageManager, serverConfiguration, issueStoreReader)).isNotNull();
  }

  @Test
  public void update_module_list() {
    when(storageManager.getModuleListPath()).thenReturn(temp.getRoot().toPath());
    updater.updateModuleList();
    verify(moduleListDownloader).fetchModulesList(temp.getRoot().toPath());
  }
}
