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
package testutils;

import java.lang.management.ManagementFactory;
import org.sonarsource.sonarlint.core.ServerApiProvider;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.http.ConnectionAwareHttpClientProvider;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class TestUtils {

  private static String generateThreadDump() {
    final var dump = new StringBuilder();
    final var threadMXBean = ManagementFactory.getThreadMXBean();
    final var threadInfos = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), 100);
    for (var threadInfo : threadInfos) {
      dump.append('"');
      dump.append(threadInfo.getThreadName());
      dump.append("\" ");
      final var state = threadInfo.getThreadState();
      dump.append("\n   java.lang.Thread.State: ");
      dump.append(state);
      final var stackTraceElements = threadInfo.getStackTrace();
      for (final var stackTraceElement : stackTraceElements) {
        dump.append("\n        at ");
        dump.append(stackTraceElement);
      }
      dump.append("\n\n");
    }
    return dump.toString();
  }

  public static void printThreadDump() {
    System.out.println(generateThreadDump());
  }

  public static ServerApiProvider mockServerApiProvider() {
    var connectionRepository = mock(ConnectionConfigurationRepository.class);
    var awareHttpClientProvider = mock(ConnectionAwareHttpClientProvider.class);
    var httpClientProvider = mock(HttpClientProvider.class);
    var sonarCloudActiveEnvironment = mock(SonarCloudActiveEnvironment.class);
    var client = mock(SonarLintRpcClient.class);
    var obj = new ServerApiProvider(connectionRepository, awareHttpClientProvider, httpClientProvider,
      sonarCloudActiveEnvironment, client);
    return spy(obj);
  }
}
