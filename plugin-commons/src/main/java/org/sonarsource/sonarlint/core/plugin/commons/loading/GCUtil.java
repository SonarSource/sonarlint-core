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
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.sonarsource.sonarlint.core.plugin.commons.loading;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class GCUtil {

  static boolean allocateTonsOfMemory(StringBuilder log, Runnable runWhileWaiting, BooleanSupplier until) {
    var freeMemory = Runtime.getRuntime().freeMemory();
    log.append("Free memory: " + freeMemory + "\n");

    int liveChunks = 0;
    ReferenceQueue<Object> queue = new ReferenceQueue<>();

    List<SoftReference<?>> list = new ArrayList<>();
    try {
      for (int i = 0; i < 1000 && !until.getAsBoolean(); i++) {
        runWhileWaiting.run();
        while (queue.poll() != null) {
          liveChunks--;
        }

        // full gc is caused by allocation of large enough array below, SoftReference will be cleared after two full gc
        int bytes = Math.min((int) (Runtime.getRuntime().totalMemory() / 20), Integer.MAX_VALUE / 2);
        log.append("Iteration " + i + ", allocating new byte[" + bytes + "]" +
          ", live chunks: " + liveChunks +
          ", free memory: " + Runtime.getRuntime().freeMemory() + "\n");

        list.add(new SoftReference<Object>(new byte[bytes], queue));
        liveChunks++;

        if (i > 0 && i % 100 == 0 && !until.getAsBoolean()) {
          log.append("  Calling System.gc()\n");
          System.gc();
        }
      }
    } catch (OutOfMemoryError e) {
      int size = list.size();
      list.clear();
      // noinspection CallToPrintStackTrace
      e.printStackTrace();
      System.err.println("Log: " + log + "freeMemory() now: " + Runtime.getRuntime().freeMemory() + "; list.size(): " + size);
      throw e;
    } finally {
      // do not leave a chance for our created SoftReference's content to lie around until next full GC
      for (Reference<?> createdReference : list) {
        createdReference.clear();
      }
    }
    return until.getAsBoolean();
  }
}
