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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class PersistentIssueTrackerCache implements IssueTrackerCache {

  private static final Logger LOGGER = Loggers.get(PersistentIssueTrackerCache.class);

  static final int MAX_ENTRIES = 100;

  private final IssueStore store;
  private final Map<String, Collection<Trackable>> cache;

  public PersistentIssueTrackerCache(IssueStore store) {
    this.store = store;
    this.cache = new LimitedSizeLinkedHashMap();
  }

  /**
   * Keeps a maximum number of entries in the map. On insertion, if the limit is passed, the entry accessed the longest time ago
   * is flushed into cache and removed from the map.
   */
  private class LimitedSizeLinkedHashMap extends LinkedHashMap<String, Collection<Trackable>> {
    LimitedSizeLinkedHashMap() {
      super(MAX_ENTRIES, 0.75f, true);
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Collection<Trackable>> eldest) {
      if (size() <= MAX_ENTRIES) {
        return false;
      }

      String key = eldest.getKey();
      try {
        LOGGER.debug("Persisting issues for " + key);
        store.save(key, eldest.getValue());
      } catch (IOException e) {
        throw new IllegalStateException(String.format("Error persisting issues for %s", key), e);
      }
      return true;
    }
  }

  @Override
  public boolean isFirstAnalysis(String file) {
    return !cache.containsKey(file) && !store.contains(file);
  }

  @Override
  public synchronized Collection<Trackable> getLiveOrFail(String file) {
    Collection<Trackable> liveTrackables = cache.get(file);
    if (liveTrackables != null) {
      return liveTrackables;
    }

    throw new IllegalStateException("No issues in cache for file: " + file);
  }

  /**
   * Read issues from a file that is cached. On cache miss, it won't fallback to the persistent store.
   */
  @Override
  public synchronized Collection<Trackable> getCurrentTrackables(String file) {
    Collection<Trackable> liveTrackables = cache.get(file);
    if (liveTrackables != null) {
      return liveTrackables;
    }

    try {
      Collection<Trackable> storedTrackables = store.read(file);
      if (storedTrackables != null) {
        return Collections.unmodifiableCollection(storedTrackables);
      }
    } catch (IOException e) {
      LOGGER.error(String.format("Failed to read issues from store for file %s", file), e);
    }
    return Collections.emptyList();
  }

  @Override
  public synchronized void put(String file, Collection<Trackable> trackables) {
    cache.put(file, trackables);
  }

  @Override
  public synchronized void clear() {
    store.clear();
    cache.clear();
  }

  /**
   * Flushes all cached entries to disk.
   * It does not clear the cache.
   */
  public synchronized void flushAll() {
    LOGGER.debug("Persisting all issues");
    cache.forEach((path, trackables) -> {
      try {
        store.save(path, trackables);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to flush cache", e);
      }
    });
  }

  @Override
  public synchronized void shutdown() {
    flushAll();
  }
}
