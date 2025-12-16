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
package org.sonarsource.sonarlint.core.spring;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.sonarsource.sonarlint.core.fs.ReadThroughFileCache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableCaching
@Import({
  ReadThroughFileCache.class
})
public class CacheConfig {

  public static final String CACHE_STATS_ENABLED_PROPERTY = "SONARLINT_CACHE_STATS_ENABLED";
  private static final String SONARLINT_CACHE_SIZE = "SONARLINT_CACHE_SIZE";
  // Total string length in the cache. Each symbol is 2-4 bytes, so it should be a cap slightly above 2MB.
  private static final int DEFAULT_MAXIMUM_WEIGHT = 1_000_000;

  @Bean
  public CacheManager cacheManager(AsyncCache<Object, Object> fileContentsCache) {
    var caffeineCacheManager = new CaffeineCacheManager();
    caffeineCacheManager.registerCustomCache("fileContentsCache",
      fileContentsCache);
    return caffeineCacheManager;
  }

  @Bean
  public AsyncCache<Object, Object> fileContentsCache() {
    var caffeine = Caffeine.newBuilder();
    caffeine.<SimpleKey, String>weigher((k, v) -> StringUtils.length(v));
    caffeine.maximumWeight(NumberUtils.toInt(
      System.getenv(SONARLINT_CACHE_SIZE), DEFAULT_MAXIMUM_WEIGHT));
    if ("true".equals(System.getenv(CACHE_STATS_ENABLED_PROPERTY))) {
      caffeine.recordStats();
    }
    return caffeine.buildAsync();
  }

  @Bean
  public Supplier<CacheStats> cacheStatsProvider(AsyncCache<Object, Object> fileContentsCache) {
    return () -> fileContentsCache.synchronous().stats();
  }
}
