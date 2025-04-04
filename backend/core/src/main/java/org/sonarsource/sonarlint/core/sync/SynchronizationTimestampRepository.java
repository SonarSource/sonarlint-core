/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.sync;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class SynchronizationTimestampRepository<T> {
  private final Map<T, Instant> lastSynchronizationTimestampPerSource = new ConcurrentHashMap<>();

  public Optional<Instant> getLastSynchronizationDate(T source) {
    return Optional.ofNullable(lastSynchronizationTimestampPerSource.get(source));
  }

  public void setLastSynchronizationTimestampToNow(T source) {
    lastSynchronizationTimestampPerSource.put(source, Instant.now());
  }

  public void clearLastSynchronizationTimestamp(T source) {
    lastSynchronizationTimestampPerSource.remove(source);
  }

  public void clearLastSynchronizationTimestampIf(Predicate<T> predicate) {
    lastSynchronizationTimestampPerSource.keySet().removeIf(predicate);
  }
}
