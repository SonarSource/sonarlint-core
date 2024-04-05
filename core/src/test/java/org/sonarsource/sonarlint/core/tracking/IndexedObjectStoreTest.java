/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput.Level;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.objectstore.PathMapper;
import org.sonarsource.sonarlint.core.commons.objectstore.Reader;
import org.sonarsource.sonarlint.core.commons.objectstore.Writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// note: most methods of the subject are already tested by higher level uses
class IndexedObjectStoreTest {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void should_log_failures_to_delete_invalid_files(@TempDir Path nonEmptyDir) throws IOException {
    StoreIndex<String> index = mock(StoreIndex.class);
    when(index.keys()).thenReturn(Collections.singleton("dummy key"));

    // attempt to delete this with Files.deleteIfExists will fail
    Files.createFile(nonEmptyDir.resolve("dummy"));
    PathMapper<String> mapper = key -> nonEmptyDir;

    StoreKeyValidator<String> validator = path -> false;
    Reader<String> reader = inputStream -> "dummy";
    Writer<String> writer = (outputStream, values) -> {
    };
    var store = new IndexedObjectStore<String, String>(index, mapper, reader, writer, validator);
    store.deleteInvalid();

    var errors = logTester.logs(Level.ERROR);
    assertThat(errors).hasSize(2);
    assertThat(errors.get(0)).startsWith("failed to delete file");
    assertThat(logTester.logs(Level.DEBUG)).containsOnly("1 entries removed from the store");
  }

}
