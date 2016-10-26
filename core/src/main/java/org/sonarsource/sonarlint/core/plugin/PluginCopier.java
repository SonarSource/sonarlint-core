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
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.plugin.PluginIndexProvider.PluginReference;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;

public class PluginCopier {

  private static final ImmutableSet<String> PLUGIN_WHITELIST = ImmutableSet.of("java", "javascript", "php", "python", "cobol", "abap", "plsql", "swift", "rpg");

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
      Boolean sonarLintSupported = info.isSonarLintSupported();
      if ((sonarLintSupported != null && sonarLintSupported.booleanValue()) || isWhitelisted(info.getKey())) {
        infosByKey.put(info.getKey(), info);
      } else {
        LOG.debug("Plugin {} is not compatible with SonarLint. Skip it.", info.getKey());
      }
    }

    profiler.stopDebug();
    return infosByKey;

  }

  public static boolean isWhitelisted(String pluginKey) {
    return PLUGIN_WHITELIST.contains(pluginKey);
  }

  @VisibleForTesting
  File getFromCacheOrCopy(final PluginReference pluginReference) {
    try {
      return fileCache.get(pluginReference.getFilename(), pluginReference.getHash(), new FileDownloader(pluginReference)).toFile();
    } catch (StorageException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Fail to copy plugin " + pluginReference.getFilename() + " from URL: " + pluginReference.getDownloadUrl(), e);
    }
  }

  private static class FileDownloader implements PluginCache.Downloader {
    private PluginReference pluginReference;

    FileDownloader(PluginReference pluginReference) {
      this.pluginReference = pluginReference;
    }

    @Override
    public void download(String filename, Path toFile) throws IOException {
      URL url = pluginReference.getDownloadUrl();
      if (url == null) {
        // In connected mode plugins are always supposed to be in the cache.
        throw new StorageException("Plugin " + pluginReference.getFilename() + " not found in local storage. Please update server configuration.", null);
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Copy plugin {} to {}", url, toFile);
      } else {
        LOG.info("Copy {}", StringUtils.substringAfterLast(url.getFile(), "/"));
      }

      FileUtils.copyURLToFile(url, toFile.toFile());
    }
  }
}
