/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonar.api.scanner.Arch;
import org.sonar.api.scanner.HostPlatform;
import org.sonar.api.scanner.Os;
import org.sonar.api.utils.System2;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Detects the machine running the analysis. An unrecognized operating system or architecture is reported as
 * {@link Os#UNKNOWN} or {@link Arch#UNKNOWN}.
 */
public class SonarLintHostPlatform implements HostPlatform {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final Path OS_RELEASE = Path.of("/etc/os-release");
  private static final Path OS_RELEASE_FALLBACK = Path.of("/usr/lib/os-release");
  private static final List<String> UNAME_EXECUTABLES = List.of("/usr/bin/uname", "/bin/uname");

  private final Os os;
  private final Arch arch;

  public SonarLintHostPlatform(System2 system) {
    var osName = system.property("os.name");
    this.os = detectOs(osName, isLinux(osName) ? readOsReleaseLines(OS_RELEASE, OS_RELEASE_FALLBACK) : List.of());
    this.arch = detectArch(readRawArch(system, SonarLintHostPlatform::tryUname));
  }

  @Override
  public Os getOs() {
    return os;
  }

  @Override
  public Arch getArch() {
    return arch;
  }

  static Os detectOs(@Nullable String osName, List<String> osReleaseLines) {
    if (osName == null) {
      return Os.UNKNOWN;
    }
    var normalized = osName.toLowerCase(Locale.ENGLISH);
    if (normalized.contains("mac") || normalized.contains("darwin")) {
      return Os.MACOS;
    }
    if (normalized.contains("win")) {
      return Os.WINDOWS;
    }
    if (normalized.contains("linux")) {
      return isAlpine(osReleaseLines) ? Os.ALPINE : Os.LINUX;
    }
    if (normalized.contains("z/os")) {
      return Os.ZOS;
    }
    return Os.UNKNOWN;
  }

  static Arch detectArch(@Nullable String arch) {
    if (arch == null || arch.isBlank()) {
      return Arch.UNKNOWN;
    }
    return switch (arch.toLowerCase(Locale.ROOT).trim()) {
      case "amd64", "x86_64" -> Arch.X64;
      case "aarch64", "arm64" -> Arch.AARCH64;
      case "s390x" -> Arch.S390X;
      case "ppc64le" -> Arch.PPC64LE;
      default -> Arch.UNKNOWN;
    };
  }

  private static boolean isLinux(@Nullable String osName) {
    return osName != null && osName.toLowerCase(Locale.ENGLISH).contains("linux");
  }

  private static boolean isAlpine(List<String> osReleaseLines) {
    return osReleaseLines.stream().anyMatch(line -> line.matches("^ID=alpine$"));
  }

  static List<String> readOsReleaseLines(Path primary, Path fallback) {
    try {
      return Files.readAllLines(primary);
    } catch (IOException primaryFailure) {
      try {
        return Files.readAllLines(fallback);
      } catch (IOException fallbackFailure) {
        primaryFailure.addSuppressed(fallbackFailure);
        LOG.debug("Failed to read the os-release file", primaryFailure);
        return List.of();
      }
    }
  }

  static String readRawArch(System2 system, Supplier<String> uname) {
    if (!system.isOsWindows()) {
      var fromUname = uname.get();
      if (fromUname != null) {
        return fromUname;
      }
    }
    return system.property("os.arch");
  }

  @Nullable
  private static String tryUname() {
    return tryUname(resolveUname(UNAME_EXECUTABLES));
  }

  @Nullable
  static String resolveUname(List<String> candidates) {
    return candidates.stream().filter(candidate -> Files.isExecutable(Path.of(candidate))).findFirst().orElse(null);
  }

  @Nullable
  static String tryUname(@Nullable String executable) {
    if (executable == null) {
      return null;
    }
    return tryUname(() -> new ProcessBuilder(executable, "-m").redirectErrorStream(true).start());
  }

  @Nullable
  static String tryUname(ProcessStarter starter) {
    Process process = null;
    try {
      process = starter.start();
      if (!process.waitFor(2, TimeUnit.SECONDS)) {
        return null;
      }
      var arch = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      if (process.exitValue() != 0 || arch.isEmpty()) {
        return null;
      }
      return arch;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.debug("Failed to get the architecture using 'uname -m'", e);
      return null;
    } catch (IOException e) {
      LOG.debug("Failed to get the architecture using 'uname -m'", e);
      return null;
    } finally {
      if (process != null) {
        process.destroy();
      }
    }
  }

  @FunctionalInterface
  interface ProcessStarter {
    Process start() throws IOException;
  }

}
