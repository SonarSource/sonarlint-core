/*
 * SonarLint Core - Plugin Commons
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
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.sonarsource.sonarlint.core.plugin.commons.loading;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A utility to garbage-collect specified objects in tests. Create a GCWatcher using {@link #tracking} or {@link #fromClearedRef}
 * and then call {@link #ensureCollected()}. Please ensure that your test doesn't hold references to objects passed to {@link #tracking},
 * so, if you pass fields or local variables there, nullify them before calling {@link #ensureCollected()}.
 *
 */
public final class GCWatcher {
  private final ReferenceQueue<Object> myQueue = new ReferenceQueue<>();
  private final Set<Reference<?>> myReferences = Collections.newSetFromMap(new ConcurrentHashMap<>());

  private GCWatcher(Collection<?> objects) {
    for (Object o : objects) {
      if (o != null) {
        myReferences.add(new WeakReference<>(o, myQueue));
      }
    }
  }

  public static GCWatcher tracking(Object... objects) {
    return tracking(Arrays.asList(objects));
  }

  public static GCWatcher tracking(Collection<?> objects) {
    return new GCWatcher(objects);
  }

  private boolean isEverythingCollected() {
    while (true) {
      Reference<?> ref = myQueue.poll();
      if (ref == null)
        return myReferences.isEmpty();

      boolean removed = myReferences.remove(ref);
      assert removed;
    }
  }

  public boolean tryCollect(int timeoutMs) {
    long startTime = System.currentTimeMillis();
    GCUtil.allocateTonsOfMemory(new StringBuilder(), () -> {
    },
      () -> isEverythingCollected() || System.currentTimeMillis() - startTime > timeoutMs);
    return isEverythingCollected();
  }

}
