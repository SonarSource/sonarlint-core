/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.spring;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.ProxySelector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.net.ssl.X509TrustManager;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.serverconnection.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "org.sonarsource.sonarlint.core")
public class SonarLintSpringAppConfig {

  private final ExecutorService eventExecutorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "SonarLint Client Events Processor"));

  @Bean
  EventBus provideClientEventBus() {
    return new AsyncEventBus("clientEvents", eventExecutorService);
  }

  @PreDestroy
  void stopClientEventBus() {
    MoreExecutors.shutdownAndAwaitTermination(eventExecutorService, 1, TimeUnit.SECONDS);
  }

  @Bean(destroyMethod = "close")
  StorageService provideStorageService(InitializeParams params, @Named("workDir") Path workDir) {
    return new StorageService(params.getStorageRoot(), workDir);
  }

  @Bean
  TelemetryServiceImpl provideTelemetryService(InitializeParams params, @Named("userHome") Path sonarlintUserHome) {
    return new TelemetryServiceImpl(params.getTelemetryProductKey(), sonarlintUserHome);
  }

  @Bean(name = "userHome")
  Path provideSonarLintUserHome(InitializeParams params) {
    var sonarlintUserHome = Optional.ofNullable(params.getSonarlintUserHome()).map(Paths::get).orElse(SonarLintUserHome.get());
    createFolderIfNeeded(sonarlintUserHome);
    return sonarlintUserHome;
  }

  @Bean(name = "workDir")
  Path provideSonarLintWorkDir(InitializeParams params, @Named("userHome") Path sonarlintUserHome) {
    var workDir = Optional.ofNullable(params.getWorkDir()).orElse(sonarlintUserHome.resolve("work"));
    createFolderIfNeeded(workDir);
    return workDir;
  }

  @Bean
  HttpClientProvider provideHttpClientProvider(InitializeParams params, X509TrustManager confirmingTrustManager, ProxySelector proxySelector,
    CredentialsProvider proxyCredentialsProvider) {
    return new HttpClientProvider(params.getUserAgent(), confirmingTrustManager, proxySelector, proxyCredentialsProvider);
  }

  private static void createFolderIfNeeded(Path path) {
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create directory '" + path + "'", e);
    }
  }

}
