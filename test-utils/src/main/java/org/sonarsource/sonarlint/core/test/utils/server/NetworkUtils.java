/*
 * SonarLint Core - Test Utils
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.test.utils.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

public final class NetworkUtils {

  private static final Set<Integer> ALREADY_ALLOCATED = new HashSet<>();
  private static final int MAX_TRIES = 50;

  private static final Set<Integer> HTTP_BLOCKED_PORTS = Set.of(2_049, 4_045, 6_000);

  private NetworkUtils() {
    // prevent instantiation
  }

  public static int getNextAvailablePort() {
    return getNextAvailablePort(getLocalhost());
  }

  static int getNextAvailablePort(InetAddress inetAddress) {
    return getNextAvailablePort(inetAddress, new PortAllocator());
  }

  private static InetAddress getLocalhost() {
    try {
      return InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      throw new IllegalStateException("Fail to get localhost IP", e);
    }
  }

  static int getNextAvailablePort(InetAddress address, PortAllocator portAllocator) {
    for (var i = 0; i < MAX_TRIES; i++) {
      int port = portAllocator.getAvailable(address);
      if (isValidPort(port)) {
        ALREADY_ALLOCATED.add(port);
        return port;
      }
    }
    throw new IllegalStateException("Fail to find an available port on " + address);
  }

  private static boolean isValidPort(int port) {
    return port > 1023 && !HTTP_BLOCKED_PORTS.contains(port) && !ALREADY_ALLOCATED.contains(port);
  }

  static class PortAllocator {

    int getAvailable(InetAddress address) {
      try (var socket = new ServerSocket(0, 50, address)) {
        return socket.getLocalPort();
      } catch (IOException e) {
        throw new IllegalStateException("Fail to find an available port on " + address, e);
      }
    }
  }
}
