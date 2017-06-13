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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.plugin.PluginIndex;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;

/**
 * Provides the list of plugins provided by the client.
 * The plugins are cached before being used.
 */
public class StandalonePluginIndex implements PluginIndex {
  private static final Logger LOG = Loggers.get(StandalonePluginIndex.class);
  private final StandalonePluginUrls pluginUrls;
  private final PluginCache fileCache;

  public StandalonePluginIndex(StandalonePluginUrls pluginUrls, PluginCache fileCache) {
    this.pluginUrls = pluginUrls;
    this.fileCache = fileCache;
  }

  @Override
  public List<PluginReference> references() {
    return pluginUrls.urls().stream()
      .map(this::getFromCacheOrCopy)
      .collect(Collectors.toList());
  }

  private PluginReference getFromCacheOrCopy(final URL pluginUrl) {
    try (InputStream is = pluginUrl.openStream()) {
      String hash = org.sonarsource.sonarlint.core.util.StringUtils.md5(is);
      String filename = StringUtils.substringAfterLast(pluginUrl.getFile(), "/");
      fileCache.get(filename, hash, new FileCopier(pluginUrl));
      return new PluginReference(hash, filename);
    } catch (StorageException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Fail to copy plugin from URL: " + pluginUrl, e);
    }
  }

  private static class FileCopier implements PluginCache.Copier {
    private final URL url;

    FileCopier(URL pluginUrl) {
      this.url = pluginUrl;
    }

    @Override
    public void copy(String filename, Path toFile) throws IOException {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Copy plugin {} to {}", url, toFile);
      } else {
        LOG.info("Copy {}", StringUtils.substringAfterLast(url.getFile(), "/"));
      }

      FileUtils.copyURLToFile(url, toFile.toFile());
    }
  }
}
