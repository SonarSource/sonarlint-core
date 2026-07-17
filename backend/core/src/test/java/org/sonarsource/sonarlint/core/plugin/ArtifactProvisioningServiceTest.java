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

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactPlan;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactDownload;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactDownloadCoordinator;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactLocation;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.AvailableArtifact;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ArtifactProvisioningServiceTest {

  private final ExecutorService executor = Executors.newFixedThreadPool(2);

  @AfterEach
  void stopExecutor() {
    executor.shutdownNow();
  }

  @Test
  void should_publish_each_download_result_immediately_and_synchronize_once_after_the_batch() throws Exception {
    var successStarted = new CountDownLatch(1);
    var successProceed = new CountDownLatch(1);
    var failureStarted = new CountDownLatch(1);
    var failureProceed = new CountDownLatch(1);
    var cppDownload = download("cpp-url", () -> {
      successStarted.countDown();
      assertThat(successProceed.await(5, TimeUnit.SECONDS)).isTrue();
      return new ArtifactLocation.Local(Path.of("cpp.jar"), ArtifactOrigin.ON_DEMAND, null);
    });
    var javaDownload = download("java-url", () -> {
      failureStarted.countDown();
      assertThat(failureProceed.await(5, TimeUnit.SECONDS)).isTrue();
      throw new IllegalStateException("download failed");
    });
    var events = new CopyOnWriteArrayList<Object>();
    ApplicationEventPublisher eventPublisher = events::add;
    var service = new ArtifactProvisioningService(new ArtifactDownloadCoordinator(executor), eventPublisher);
    var context = new PluginContext.Connected("connection");

    var state = service.getOrProvision(context, () -> plan(cppDownload, javaDownload));

    assertThat(successStarted.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(failureStarted.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(statusEvents(events)).singleElement().satisfies(event -> {
      assertThat(statusFor(event, SonarLanguage.CPP).state()).isEqualTo(ArtifactState.DOWNLOADING);
      assertThat(statusFor(event, SonarLanguage.JAVA).state()).isEqualTo(ArtifactState.DOWNLOADING);
    });

    successProceed.countDown();
    await().atMost(5, TimeUnit.SECONDS).until(() -> statusEvents(events).size() == 2);
    assertThat(statusFor(statusEvents(events).get(1), SonarLanguage.CPP).state()).isEqualTo(ArtifactState.ACTIVE);
    assertThat(events).noneMatch(PluginsSynchronizedEvent.class::isInstance);

    failureProceed.countDown();
    state.downloadBatch().orElseThrow().completion().get(5, TimeUnit.SECONDS);
    await().atMost(5, TimeUnit.SECONDS).until(() -> statusEvents(events).size() == 3);

    assertThat(statusFor(statusEvents(events).get(2), SonarLanguage.JAVA).state()).isEqualTo(ArtifactState.FAILED);
    assertThat(events).filteredOn(PluginsSynchronizedEvent.class::isInstance)
      .containsExactly(new PluginsSynchronizedEvent("connection"));
  }

  @Test
  void should_reuse_provisioning_state_for_the_same_context() {
    var events = new CopyOnWriteArrayList<Object>();
    var service = new ArtifactProvisioningService(new ArtifactDownloadCoordinator(executor), events::add);
    var context = new PluginContext.Connected("connection");
    var supplierCalls = new java.util.concurrent.atomic.AtomicInteger();

    var first = service.getOrProvision(context, () -> {
      supplierCalls.incrementAndGet();
      return new ArtifactPlan(EnumSet.noneOf(SonarLanguage.class), Map.of(), Map.of());
    });
    var second = service.getOrProvision(context, () -> {
      supplierCalls.incrementAndGet();
      return new ArtifactPlan(EnumSet.noneOf(SonarLanguage.class), Map.of(), Map.of());
    });

    assertThat(second).isSameAs(first);
    assertThat(supplierCalls).hasValue(1);
    assertThat(statusEvents(events)).hasSize(1);
  }

  @Test
  void should_expose_the_download_batch_atomically_with_the_provisioning_state() throws Exception {
    var initialEventPublishing = new CountDownLatch(1);
    var allowInitialEvent = new CountDownLatch(1);
    var firstStatusEvent = new AtomicBoolean(true);
    ApplicationEventPublisher eventPublisher = event -> {
      if (event instanceof PluginStatusesChangedEvent && firstStatusEvent.compareAndSet(true, false)) {
        initialEventPublishing.countDown();
        try {
          assertThat(allowInitialEvent.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }
    };
    var service = new ArtifactProvisioningService(new ArtifactDownloadCoordinator(executor), eventPublisher);
    var context = new PluginContext.Connected("connection");
    var download = download("java-url", () -> new ArtifactLocation.Local(Path.of("java.jar"), ArtifactOrigin.SONARQUBE_SERVER, null));
    var plan = new ArtifactPlan(EnumSet.of(SonarLanguage.JAVA), Map.of("java", remote("java", download)), Map.of());

    var firstLookup = executor.submit(() -> service.getOrProvision(context, () -> plan));
    assertThat(initialEventPublishing.await(5, TimeUnit.SECONDS)).isTrue();

    try {
      var concurrentLookup = service.getOrProvision(context, () -> plan);
      assertThat(concurrentLookup.downloadBatch()).isPresent();
    } finally {
      allowInitialEvent.countDown();
    }
    firstLookup.get(5, TimeUnit.SECONDS);
  }

  private static ArtifactPlan plan(ArtifactDownload cppDownload, ArtifactDownload javaDownload) {
    var artifacts = new LinkedHashMap<String, AvailableArtifact>();
    artifacts.put("cpp", remote("cpp", cppDownload));
    artifacts.put("java", remote("java", javaDownload));
    return new ArtifactPlan(EnumSet.of(SonarLanguage.CPP, SonarLanguage.JAVA), artifacts, Map.of());
  }

  private static AvailableArtifact remote(String key, ArtifactDownload download) {
    return new AvailableArtifact(key, null, false, Optional.empty(), new ArtifactLocation.Remote(download));
  }

  private static ArtifactDownload download(String key, DownloadAction action) {
    return new ArtifactDownload() {
      @Override
      public String deduplicationKey() {
        return key;
      }

      @Override
      public ArtifactLocation.Local download() throws Exception {
        return action.run();
      }
    };
  }

  private static java.util.List<PluginStatusesChangedEvent> statusEvents(java.util.List<Object> events) {
    return events.stream().filter(PluginStatusesChangedEvent.class::isInstance).map(PluginStatusesChangedEvent.class::cast).toList();
  }

  private static PluginStatus statusFor(PluginStatusesChangedEvent event, SonarLanguage language) {
    return event.pluginStatuses().stream().filter(status -> status.language() == language).findFirst().orElseThrow();
  }

  private interface DownloadAction {
    ArtifactLocation.Local run() throws Exception;
  }
}
