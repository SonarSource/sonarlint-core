/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PersistentIssueTrackerCacheTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private PersistentIssueTrackerCache cache;
  private StubIssueStore stubIssueStore;

  class StubIssueStore extends IssueStore {
    private final Map<String, Collection<Trackable>> cache = new HashMap<>();

    StubIssueStore() throws IOException {
      super(temporaryFolder.newFolder().toPath(), temporaryFolder.newFolder().toPath());
    }

    @Override
    public void save(String key, Collection<Trackable> issues) throws IOException {
      cache.put(key, issues);
    }

    @Override
    public Collection<Trackable> read(String key) throws IOException {
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

  @Before
  public void setUp() throws IOException {
    stubIssueStore = new StubIssueStore();
    cache = new PersistentIssueTrackerCache(stubIssueStore);
  }

  @Test
  public void should_persist_issues_when_inmemory_limit_reached() {
    int i = 0;
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
  public void should_persist_issues_on_shutdown() {
    int count = PersistentIssueTrackerCache.MAX_ENTRIES / 2;
    for (int i = 0; i < count; i++) {
      cache.put("file" + i, Collections.emptyList());
    }
    assertThat(stubIssueStore.size()).isEqualTo(0);

    cache.shutdown();
    assertThat(stubIssueStore.size()).isEqualTo(count);
  }

  @Test
  public void should_return_empty_for_file_never_analyzed() {
    String file = "nonexistent";
    assertThat(cache.isFirstAnalysis(file)).isTrue();
    assertThat(cache.getCurrentTrackables(file)).isEmpty();
  }

  @Test
  public void should_return_empty_for_file_with_no_issues_as_cached() {
    String file = "dummy file";
    cache.put(file, Collections.emptyList());
    assertThat(cache.isFirstAnalysis(file)).isFalse();
    assertThat(cache.getCurrentTrackables(file)).isEmpty();
  }

  @Test
  public void should_return_empty_for_file_with_no_issues_as_persisted() throws IOException {
    String file = "dummy file";
    stubIssueStore.save(file, Collections.emptyList());
    assertThat(cache.isFirstAnalysis(file)).isFalse();
    assertThat(cache.getCurrentTrackables(file)).isEmpty();
  }

  @Test
  public void should_clear_cache_and_storage_too() throws IOException {
    String file = "dummy file";
    cache.put(file, Collections.singletonList(mock(Trackable.class)));
    cache.flushAll();

    assertThat(cache.getCurrentTrackables(file)).isNotEmpty();
    assertThat(stubIssueStore.size()).isEqualTo(1);

    cache.clear();
    assertThat(cache.getCurrentTrackables(file)).isEmpty();
    assertThat(stubIssueStore.size()).isEqualTo(0);
  }

  @Test(expected = IllegalStateException.class)
  public void getLiveOrFail_should_fail_if_no_live_issues() {
    cache.getLiveOrFail("nonexistent");
  }

  @Test
  public void getLiveOrFail_should_return_live_issues_when_present() {
    String file = "dummy file";
    List<Trackable> trackables = Collections.singletonList(mock(Trackable.class));
    cache.put(file, trackables);
    assertThat(cache.getLiveOrFail(file)).isEqualTo(trackables);
  }

  @Test
  public void getCurrentTrackables_should_gracefully_return_empty_list_if_io_failures_during_store_read() throws IOException {
    String file = "nonexistent";
    IssueStore store = mock(IssueStore.class);
    when(store.read(file)).thenThrow(new IOException("failed to read from store"));
    IssueTrackerCache cache = new PersistentIssueTrackerCache(store);
    assertThat(cache.getCurrentTrackables(file)).isEmpty();
    verify(store).read(file);
  }

  @Test(expected = IllegalStateException.class)
  public void flushAll_should_crash_on_io_failures_during_store_write() throws IOException {
    String file = "dummy file";
    Collection<Trackable> trackables = Collections.singletonList(mock(Trackable.class));

    IssueStore store = mock(IssueStore.class);
    doThrow(new IOException("failed to write to store")).when(store).save(file, trackables);

    PersistentIssueTrackerCache cache = new PersistentIssueTrackerCache(store);
    cache.put(file, trackables);
    cache.flushAll();
    verify(store).save(file, trackables);
  }

  @Test(expected = IllegalStateException.class)
  public void put_should_crash_on_io_failures_during_store_write() throws IOException {
    IssueStore store = mock(IssueStore.class);
    doThrow(new IOException("failed to write to store")).when(store).save(anyString(), any());
    PersistentIssueTrackerCache cache = new PersistentIssueTrackerCache(store);
    for (int i = 0; i < PersistentIssueTrackerCache.MAX_ENTRIES + 1; i++) {
      cache.put("dummy" + i, Collections.emptyList());
    }
  }
}
