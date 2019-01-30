/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.tracking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.PathMapper;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Reader;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Writer;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// note: most methods of the subject are already tested by higher level uses
public class IndexedObjectStoreTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void should_log_failures_to_delete_invalid_files() throws IOException {
    StoreIndex<String> index = mock(StoreIndex.class);
    when(index.keys()).thenReturn(Collections.singleton("dummy key"));

    // attempt to delete this with Files.deleteIfExists will fail
    Path nonEmptyDir = temporaryFolder.newFolder().toPath();
    Files.createFile(nonEmptyDir.resolve("dummy"));
    PathMapper<String> mapper = key -> nonEmptyDir;

    StoreKeyValidator<String> validator = path -> false;
    Reader<String> reader = inputStream -> "dummy";
    Writer<String> writer = (outputStream, values) -> {};
    Logger logger = mock(Logger.class);
    IndexedObjectStore<String, String> store = new IndexedObjectStore<>(index, mapper, reader, writer, validator, logger);
    store.deleteInvalid();

    verify(logger).error(contains("failed to delete file"), any());
    verify(logger).debug(eq("1 entries removed from the store"));
  }

}
