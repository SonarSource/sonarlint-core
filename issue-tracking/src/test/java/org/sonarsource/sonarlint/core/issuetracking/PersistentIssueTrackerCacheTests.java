/*
 * SonarLint Issue Tracking
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
package org.sonarsource.sonarlint.core.issuetracking;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistentIssueTrackerCacheTests {

  private PersistentIssueTrackerCache cache;
  private StubIssueStore stubIssueStore;

  class StubIssueStore implements TrackableIssueStore<Object> {
    private final Map<String, Collection<Trackable<Object>>> cache = new HashMap<>();

    @Override
    public void save(String key, Collection<Trackable<Object>> issues) throws IOException {
      cache.put(key, issues);
    }

    @Override
    public Collection<Trackable<Object>> read(String key) throws IOException {
      return cache.get(key);
    }

    @Override
    public boolean contains(String key) {
      return cache.containsKey(key);
    }

    @Override
    public void clear() {
      cache.clear();
    }

    int size() {
      return cache.size();
    }
  }

  @BeforeEach
  void setUp() {
    stubIssueStore = new StubIssueStore();
    cache = new PersistentIssueTrackerCache(stubIssueStore);
  }

  @Test
  void should_persist_issues_when_inmemory_limit_reached() {
    var i = 0;
    for (; i < PersistentIssueTrackerCache.MAX_ENTRIES; i++) {
      cache.put("file" + i, Collections.emptyList());
    }
    assertThat(stubIssueStore.size()).isEqualTo(0);

    cache.put("file" + i++, Collections.emptyList());
    assertThat(stubIssueStore.size()).isEqualTo(1);

    cache.put("file" + i, Collections.emptyList());
    assertThat(stubIssueStore.size()).isEqualTo(2);
  }

  @Test
  void should_persist_issues_on_shutdown() {
    var count = PersistentIssueTrackerCache.MAX_ENTRIES / 2;
    for (var i = 0; i < count; i++) {
      cache.put("file" + i, Collections.emptyList());
    }
    assertThat(stubIssueStore.size()).isEqualTo(0);

    cache.shutdown();
    assertThat(stubIssueStore.size()).isEqualTo(count);
  }

  @Test
  void should_return_empty_for_file_never_analyzed() {
    var file = "nonexistent";
    assertThat(cache.isFirstAnalysis(file)).isTrue();
    assertThat(cache.getCurrentTrackables(file)).isEmpty();
  }

  @Test
  void should_return_empty_for_file_with_no_issues_as_cached() {
    var file = "dummy file";
    cache.put(file, Collections.emptyList());
    assertThat(cache.isFirstAnalysis(file)).isFalse();
    assertThat(cache.getCurrentTrackables(file)).isEmpty();
  }

  @Test
  void should_return_empty_for_file_with_no_issues_as_persisted() throws IOException {
    var file = "dummy file";
    stubIssueStore.save(file, Collections.emptyList());
    assertThat(cache.isFirstAnalysis(file)).isFalse();
    assertThat(cache.getCurrentTrackables(file)).isEmpty();
  }

  @Test
  void should_clear_cache_and_storage_too() throws IOException {
    var file = "dummy file";
    cache.put(file, Collections.singletonList(mock(Trackable.class)));
    cache.flushAll();

    assertThat(cache.getCurrentTrackables(file)).isNotEmpty();
    assertThat(stubIssueStore.size()).isEqualTo(1);

    cache.clear();
    assertThat(cache.getCurrentTrackables(file)).isEmpty();
    assertThat(stubIssueStore.size()).isZero();
  }

  @Test
  void getLiveOrFail_should_fail_if_no_live_issues() {
    assertThrows(IllegalStateException.class, () -> cache.getLiveOrFail("nonexistent"));
  }

  @Test
  void getLiveOrFail_should_return_live_issues_when_present() {
    var file = "dummy file";
    List<Trackable> trackables = Collections.singletonList(mock(Trackable.class));
    cache.put(file, trackables);
    assertThat(cache.getLiveOrFail(file)).isEqualTo(trackables);
  }

  @Test
  void getCurrentTrackables_should_gracefully_return_empty_list_if_io_failures_during_store_read() throws IOException {
    var file = "nonexistent";
    var store = mock(TrackableIssueStore.class);
    when(store.read(file)).thenThrow(new IOException("failed to read from store"));
    IssueTrackerCache cache = new PersistentIssueTrackerCache(store);
    assertThat(cache.getCurrentTrackables(file)).isEmpty();
    verify(store).read(file);
  }

  @Test
  void flushAll_should_crash_on_io_failures_during_store_write() throws IOException {
    var file = "dummy file";
    Collection<Trackable> trackables = Collections.singletonList(mock(Trackable.class));

    var store = mock(TrackableIssueStore.class);
    doThrow(new IOException("failed to write to store")).when(store).save(file, trackables);

    var cache = new PersistentIssueTrackerCache(store);
    cache.put(file, trackables);
    assertThrows(IllegalStateException.class, () -> cache.flushAll());
  }

  @Test
  void put_should_crash_on_io_failures_during_store_write() throws IOException {
    var store = mock(TrackableIssueStore.class);
    doThrow(new IOException("failed to write to store")).when(store).save(anyString(), any());
    var cache = new PersistentIssueTrackerCache(store);
    for (var i = 0; i < PersistentIssueTrackerCache.MAX_ENTRIES; i++) {
      cache.put("dummy" + i, Collections.emptyList());
    }
    assertThrows(IllegalStateException.class, () -> cache.put("too much", Collections.emptyList()));
  }
}
