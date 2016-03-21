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
package org.sonarsource.sonarlint.core.plugin;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.sonar.api.internal.apachecommons.lang.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonarsource.sonarlint.core.plugin.PluginIndexProvider.PluginReference;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;

public class PluginCopier {

  private static final Logger LOG = Loggers.get(PluginCopier.class);

  private final PluginCache fileCache;
  private final PluginIndexProvider pluginIndexProvider;

  public PluginCopier(PluginCache fileCache, PluginIndexProvider pluginIndexProvider) {
    this.fileCache = fileCache;
    this.pluginIndexProvider = pluginIndexProvider;
  }

  public Map<String, PluginInfo> installRemotes() {
    return loadPlugins(pluginIndexProvider.references());
  }

  private Map<String, PluginInfo> loadPlugins(List<PluginReference> pluginReferences) {
    Map<String, PluginInfo> infosByKey = new HashMap<>();

    Profiler profiler = Profiler.create(LOG).startDebug("Load plugins");

    for (PluginReference pluginReference : pluginReferences) {
      File jarFile = getFromCacheOrCopy(pluginReference);
      PluginInfo info = PluginInfo.create(jarFile);
      infosByKey.put(info.getKey(), info);
    }

    profiler.stopDebug();
    return infosByKey;
  }

  @VisibleForTesting
  File getFromCacheOrCopy(final PluginReference pluginReference) {
    final URL url = pluginReference.getDownloadUrl();
    try {
      return fileCache.get(pluginReference.getFilename(), pluginReference.getHash(), new StandaloneModeFileDownloader(url)).toFile();
    } catch (Exception e) {
      throw new IllegalStateException("Fail to copy plugin " + pluginReference.getFilename() + " from URL: " + url, e);
    }
  }

  /**
   * In connected mode plugins are always supposed to be in the cache.
   */
  private static class StandaloneModeFileDownloader implements PluginCache.Downloader {
    private URL url;

    StandaloneModeFileDownloader(URL url) {
      this.url = url;
    }

    @Override
    public void download(String filename, Path toFile) throws IOException {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Copy plugin {} to {}", url, toFile);
      } else {
        LOG.info("Copy {}", StringUtils.substringAfterLast(url.getFile(), "/"));
      }

      FileUtils.copyURLToFile(url, toFile.toFile());
    }
  }
}
