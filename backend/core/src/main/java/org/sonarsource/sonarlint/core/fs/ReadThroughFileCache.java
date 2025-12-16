/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.fs;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.sonarsource.sonarlint.core.analysis.AnalysisFinishedEvent;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;

import static org.sonarsource.sonarlint.core.spring.CacheConfig.CACHE_STATS_ENABLED_PROPERTY;

public class ReadThroughFileCache {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Supplier<CacheStats> cacheStatsProvider;

  public ReadThroughFileCache(Supplier<CacheStats> cacheStatsProvider) {
    this.cacheStatsProvider = cacheStatsProvider;
  }

  @Cacheable(cacheNames = "fileContentsCache")
  public String content(Path fsPath, Charset charset) throws IOException {
    var inputStream = BOMInputStream.builder()
      .setPath(fsPath)
      .setByteOrderMarks(ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_8, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE)
      .get();

    return IOUtils.toString(inputStream, charset);
  }

  @CacheEvict(cacheNames = "fileContentsCache", beforeInvocation = true)
  public void remove(Path fsPath, Charset charset) {
    LOG.debug("Evicted {} ({}) from file cache.", fsPath, charset);
  }

  @EventListener
  @CacheEvict(cacheNames = "fileContentsCache", allEntries = true)
  public void onAnalysisFinished(AnalysisFinishedEvent event) {
    if ("true".equals(System.getenv(CACHE_STATS_ENABLED_PROPERTY))) {
      var cacheStats = cacheStatsProvider.get();
      LOG.debug("Clearing up cache after the analysis {}, cache summary: {}",
        event.getAnalysisId(), cacheSummary(cacheStats));
    }
  }

  private static String cacheSummary(CacheStats stats) {
    return "{"
      + "hitCount:" + stats.hitCount() + ", "
      + "missCount:" + stats.missCount() + ", "
      + "evictionCount:" + stats.evictionCount() + ", "
      + "evictionWeight:" + stats.evictionWeight()
      + "}";
  }
}
