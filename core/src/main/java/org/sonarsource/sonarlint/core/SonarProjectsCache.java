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
package org.sonarsource.sonarlint.core;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.eventbus.Subscribe;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.client.api.util.TextSearchIndex;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationRemovedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionConfigurationUpdatedEvent;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;

import static org.sonarsource.sonarlint.core.commons.log.SonarLintLogger.singlePlural;

public class SonarProjectsCache {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ServerApiProvider serverApiProvider;

  private final Cache<String, TextSearchIndex<ServerProject>> textSearchIndexCache = CacheBuilder.newBuilder()
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build();

  private final Cache<SonarProjectKey, Optional<ServerProject>> singleProjectsCache = CacheBuilder.newBuilder()
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build();

  private static class SonarProjectKey {
    private final String connectionId;
    private final String projectKey;

    private SonarProjectKey(String connectionId, String projectKey) {
      this.connectionId = connectionId;
      this.projectKey = projectKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      var that = (SonarProjectKey) o;
      return connectionId.equals(that.connectionId) && projectKey.equals(that.projectKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(connectionId, projectKey);
    }
  }

  public SonarProjectsCache(ServerApiProvider serverApiProvider) {
    this.serverApiProvider = serverApiProvider;
  }

  @Subscribe
  public void connectionRemoved(ConnectionConfigurationRemovedEvent e) {
    evictAll(e.getRemovedConnectionId());
  }

  @Subscribe
  public void connectionUpdated(ConnectionConfigurationUpdatedEvent e) {
    // If connection config was modified (url, credentials, ...) then the projects the user might be able to "see" could be different
    evictAll(e.getUpdatedConnectionId());
  }

  private void evictAll(String connectionId) {
    textSearchIndexCache.invalidate(connectionId);
    // Not possible to evict only entries of the given connection, so simply evict all
    singleProjectsCache.invalidateAll();
  }

  public Optional<ServerProject> getSonarProject(String connectionId, String sonarProjectKey) {
    try {
      return singleProjectsCache.get(new SonarProjectKey(connectionId, sonarProjectKey), () -> {
        LOG.debug("Query project '{}' on connection '{}'...", sonarProjectKey, connectionId);
        try {
          return serverApiProvider.getServerApi(connectionId).flatMap(s -> s.component().getProject(sonarProjectKey));
        } catch (Exception e) {
          LOG.error("Error while querying project '{}' from connection '{}'", sonarProjectKey, connectionId, e);
          return Optional.empty();
        }
      });
    } catch (ExecutionException e) {
      throw new IllegalStateException(e.getCause());
    }
  }

  public TextSearchIndex<ServerProject> getTextSearchIndex(String connectionId) {
    try {
      return textSearchIndexCache.get(connectionId, () -> {
        LOG.debug("Load projects from connection '{}'...", connectionId);
        List<ServerProject> projects;
        try {
          projects = serverApiProvider.getServerApi(connectionId).map(s -> s.component().getAllProjects(new ProgressMonitor(null))).orElse(List.of());
        } catch (Exception e) {
          LOG.error("Error while querying projects from connection '{}'", connectionId, e);
          return new TextSearchIndex<>();
        }
        if (projects.isEmpty()) {
          LOG.debug("No projects found for connection '{}'", connectionId);
          return new TextSearchIndex<>();
        } else {
          LOG.debug("Creating index for {} {}", projects.size(), singlePlural(projects.size(), "project", "projects"));
          var index = new TextSearchIndex<ServerProject>();
          projects.forEach(p -> index.index(p, p.getKey() + " " + p.getName()));
          return index;
        }
      });
    } catch (ExecutionException e) {
      throw new IllegalStateException(e.getCause());
    }
  }

}
