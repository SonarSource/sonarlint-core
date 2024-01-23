/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package testutils;

import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import static testutils.TestUtils.printThreadDump;

public class ThreadDumpExtension implements InvocationInterceptor {
  private static final ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);

  @Override
  public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
    var timeout = invocationContext.getExecutable().getAnnotation(TakeThreadDumpAfter.class);
    var clazz = invocationContext.getExecutable().getDeclaringClass();
    while (timeout == null && clazz != Object.class) {
      timeout = clazz.getAnnotation(TakeThreadDumpAfter.class);
      clazz = clazz.getSuperclass();
    }
    if (timeout == null || timeout.seconds() <= 0) {
      invocation.proceed();
      return;
    }
    var seconds = timeout.seconds();
    var caller = Thread.currentThread();
    var timedOut = new AtomicBoolean();
    Future<Void> future = exec.schedule(() -> {
      System.out.println("**** TIMEOUT ERROR: TEST EXCEEDED " + seconds + " SECONDS ****");
      printThreadDump();
      timedOut.set(true);
      caller.interrupt();
      return null;
    }, seconds, TimeUnit.SECONDS);
    Exception caught = null;
    try {
      invocation.proceed();
    } catch (Exception ex) {
      caught = ex;
    } finally {
      future.cancel(true);
      if (timedOut.get()) {
        if (!timeout.expectTimeout()) {
          Exception ex = new TimeoutException("Test exceeded timeout of " + seconds + " seconds");
          if (caught != null) {
            ex.addSuppressed(caught);
          }
          throw ex;
        }
      } else if (caught != null) {
        throw caught;
      } else if (timeout.expectTimeout()) {
        throw new RuntimeException("Test expected to timeout at " + seconds + " but didn't");
      }
    }
  }
}
