/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.standalone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.plugin.PluginIndex.PluginReference;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;

public class StandalonePluginIndexTest {
  private StandalonePluginIndex index;
  private PluginCache cache;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void setUp() throws IOException {
    File file = temp.newFile("filename");
    FileUtils.write(file, "string");
    URL plugin = file.toURI().toURL();
    StandalonePluginUrls urls = new StandalonePluginUrls(Collections.singletonList(plugin));
    cache = mock(PluginCache.class);
    index = new StandalonePluginIndex(urls, cache);
  }

  @Test
  public void testLoading() {
    List<PluginReference> references = index.references();
    assertThat(references).hasSize(1);

    verify(cache).get(eq("filename"), eq("b45cffe084dd3d20d928bee85e7b0f21"), any(PluginCache.Copier.class));
  }

  @Test
  public void testCacheStorageError() {
    when(cache.get(eq("filename"), eq("b45cffe084dd3d20d928bee85e7b0f21"), any(PluginCache.Copier.class))).thenThrow(new StorageException("msg", true));
    exception.expect(StorageException.class);
    exception.expectMessage("msg");
    index.references();
  }

  @Test
  public void testCacheOtherError() {
    when(cache.get(eq("filename"), eq("b45cffe084dd3d20d928bee85e7b0f21"), any(PluginCache.Copier.class))).thenThrow(new IllegalArgumentException("msg"));
    exception.expect(IllegalStateException.class);
    exception.expectMessage("Fail to copy plugin");
    index.references();
  }
}
