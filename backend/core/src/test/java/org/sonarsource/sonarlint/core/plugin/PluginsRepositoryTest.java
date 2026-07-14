/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.plugin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PluginsRepositoryTest {

  @Test
  void should_load_only_once_for_concurrent_requests_in_the_same_context() throws Exception {
    var repository = new PluginsRepository();
    var context = new PluginContext.Connected("connection");
    var configuration = mock(PluginsConfiguration.class);
    var loaderStarted = new CountDownLatch(1);
    var proceed = new CountDownLatch(1);
    var loaderCalls = new AtomicInteger();
    try (var executor = Executors.newFixedThreadPool(4)) {
      var requests = java.util.stream.IntStream.range(0, 4)
        .mapToObj(ignored -> CompletableFuture.supplyAsync(() -> repository.getOrLoad(context, () -> {
          loaderCalls.incrementAndGet();
          loaderStarted.countDown();
          await(proceed);
          return configuration;
        }), executor))
        .toList();

      assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
      proceed.countDown();
      CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);

      assertThat(loaderCalls).hasValue(1);
      assertThat(requests).extracting(CompletableFuture::join)
        .extracting(PluginsRepository.CacheLookup::configuration)
        .containsOnly(configuration);
      assertThat(requests).extracting(CompletableFuture::join)
        .filteredOn(PluginsRepository.CacheLookup::created)
        .hasSize(1);
    }
  }

  @Test
  void should_load_different_contexts_independently() throws Exception {
    var repository = new PluginsRepository();
    var firstStarted = new CountDownLatch(1);
    var firstProceed = new CountDownLatch(1);
    var firstConfiguration = mock(PluginsConfiguration.class);
    var secondConfiguration = mock(PluginsConfiguration.class);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = CompletableFuture.supplyAsync(() -> repository.getOrLoad(new PluginContext.Connected("first"), () -> {
        firstStarted.countDown();
        await(firstProceed);
        return firstConfiguration;
      }), executor);
      assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

      var second = CompletableFuture.supplyAsync(
        () -> repository.getOrLoad(new PluginContext.Connected("second"), () -> secondConfiguration), executor);

      assertThat(second.get(1, TimeUnit.SECONDS).configuration()).isSameAs(secondConfiguration);
      assertThat(first).isNotDone();
      firstProceed.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS).configuration()).isSameAs(firstConfiguration);
    }
  }

  @Test
  void eviction_racing_with_a_load_should_not_leave_the_loaded_configuration_cached() throws Exception {
    var repository = new PluginsRepository();
    var context = new PluginContext.Standalone();
    var staleLoadedPlugins = mock(LoadedPlugins.class);
    var staleConfiguration = configuration(staleLoadedPlugins);
    var freshConfiguration = mock(PluginsConfiguration.class);
    var loaderStarted = new CountDownLatch(1);
    var proceed = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var load = CompletableFuture.supplyAsync(() -> repository.getOrLoad(context, () -> {
        loaderStarted.countDown();
        await(proceed);
        return staleConfiguration;
      }), executor);
      assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
      var eviction = CompletableFuture.runAsync(() -> repository.evict(context), executor);

      assertThat(eviction).isNotDone();
      proceed.countDown();
      load.get(5, TimeUnit.SECONDS);
      eviction.get(5, TimeUnit.SECONDS);

      var afterEviction = repository.getOrLoad(context, () -> freshConfiguration);
      assertThat(afterEviction.created()).isTrue();
      assertThat(afterEviction.configuration()).isSameAs(freshConfiguration);
      verify(staleLoadedPlugins).close();
    }
  }

  @Test
  void should_close_plugins_on_eviction_while_the_repository_still_owns_them() throws Exception {
    var repository = new PluginsRepository();
    var context = new PluginContext.Standalone();
    var loadedPlugins = mock(LoadedPlugins.class);
    repository.getOrLoad(context, () -> configuration(loadedPlugins));

    repository.evict(context);

    verify(loadedPlugins).close();
  }

  @Test
  void should_not_close_plugins_on_eviction_after_ownership_was_transferred() throws Exception {
    var repository = new PluginsRepository();
    var context = new PluginContext.Connected("connection");
    var loadedPlugins = mock(LoadedPlugins.class);
    var configuration = repository.getOrLoad(context, () -> configuration(loadedPlugins)).configuration();
    repository.transferOwnership(context, configuration);

    repository.evict(context);

    verify(loadedPlugins, never()).close();
  }

  @Test
  void should_close_only_repository_owned_plugins_when_unloading_all() throws Exception {
    var repository = new PluginsRepository();
    var repositoryOwnedPlugins = mock(LoadedPlugins.class);
    var schedulerOwnedPlugins = mock(LoadedPlugins.class);
    var connectedContext = new PluginContext.Connected("connection");
    repository.getOrLoad(new PluginContext.Standalone(), () -> configuration(repositoryOwnedPlugins));
    var schedulerOwnedConfiguration = repository.getOrLoad(connectedContext, () -> configuration(schedulerOwnedPlugins)).configuration();
    repository.transferOwnership(connectedContext, schedulerOwnedConfiguration);

    repository.unloadAllPlugins();

    verify(repositoryOwnedPlugins).close();
    verify(schedulerOwnedPlugins, never()).close();
  }

  @Test
  void should_reject_ownership_transfer_after_eviction() throws Exception {
    var repository = new PluginsRepository();
    var context = new PluginContext.Standalone();
    var configuration = repository.getOrLoad(context, () -> configuration(mock(LoadedPlugins.class))).configuration();
    repository.evict(context);

    assertThatThrownBy(() -> repository.transferOwnership(context, configuration))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot transfer ownership of a plugin configuration that is no longer cached");
  }

  private static PluginsConfiguration configuration(LoadedPlugins loadedPlugins) {
    return new PluginsConfiguration(null, loadedPlugins, Map.of());
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
