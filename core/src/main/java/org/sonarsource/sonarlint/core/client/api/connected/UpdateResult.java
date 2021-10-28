/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.util.Collection;
import java.util.List;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.UpdateEvent;

public class UpdateResult {
  private final GlobalStorageStatus status;
  private final Collection<SonarAnalyzer> analyzers;
  private final List<UpdateEvent> updateEvents;

  public UpdateResult(GlobalStorageStatus status, Collection<SonarAnalyzer> analyzers, List<UpdateEvent> updateEvents) {
    this.status = status;
    this.analyzers = analyzers;
    this.updateEvents = updateEvents;
  }

  public GlobalStorageStatus status() {
    return status;
  }

  public Collection<SonarAnalyzer> analyzers() {
    return analyzers;
  }

  public List<UpdateEvent> updateEvents() {
    return updateEvents;
  }

  public boolean didUpdateAnalyzers() {
    return updateEvents.stream().anyMatch(event -> event instanceof PluginReferencesDownloader.PluginEvent);
  }
}
