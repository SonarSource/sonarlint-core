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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.scanner.Arch;
import org.sonar.api.scanner.Os;
import org.sonar.api.utils.System2;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarLintHostPlatformTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void detects_os_from_os_name() {
    assertThat(SonarLintHostPlatform.detectOs("Mac OS X", List.of())).isEqualTo(Os.MACOS);
    assertThat(SonarLintHostPlatform.detectOs("Darwin", List.of())).isEqualTo(Os.MACOS);
    assertThat(SonarLintHostPlatform.detectOs("Windows 11", List.of())).isEqualTo(Os.WINDOWS);
    assertThat(SonarLintHostPlatform.detectOs("Linux", List.of())).isEqualTo(Os.LINUX);
    assertThat(SonarLintHostPlatform.detectOs("Linux", List.of("NAME=Alpine", "ID=alpine"))).isEqualTo(Os.ALPINE);
    assertThat(SonarLintHostPlatform.detectOs("z/OS", List.of())).isEqualTo(Os.ZOS);
    assertThat(SonarLintHostPlatform.detectOs("FreeBSD", List.of())).isEqualTo(Os.UNKNOWN);
    assertThat(SonarLintHostPlatform.detectOs(null, List.of())).isEqualTo(Os.UNKNOWN);
  }

  @Test
  void alpine_requires_an_exact_id_line() {
    assertThat(SonarLintHostPlatform.detectOs("Linux", List.of("ID=\"alpine\""))).isEqualTo(Os.LINUX);
    assertThat(SonarLintHostPlatform.detectOs("Linux", List.of("ID=alpine "))).isEqualTo(Os.LINUX);
  }

  @Test
  void detects_arch_from_the_raw_value() {
    assertThat(SonarLintHostPlatform.detectArch("amd64")).isEqualTo(Arch.X64);
    assertThat(SonarLintHostPlatform.detectArch("x86_64")).isEqualTo(Arch.X64);
    assertThat(SonarLintHostPlatform.detectArch("aarch64")).isEqualTo(Arch.AARCH64);
    assertThat(SonarLintHostPlatform.detectArch("ARM64")).isEqualTo(Arch.AARCH64);
    assertThat(SonarLintHostPlatform.detectArch("s390x")).isEqualTo(Arch.S390X);
    assertThat(SonarLintHostPlatform.detectArch("ppc64le")).isEqualTo(Arch.PPC64LE);
    assertThat(SonarLintHostPlatform.detectArch("x86")).isEqualTo(Arch.UNKNOWN);
    assertThat(SonarLintHostPlatform.detectArch("  ")).isEqualTo(Arch.UNKNOWN);
    assertThat(SonarLintHostPlatform.detectArch(null)).isEqualTo(Arch.UNKNOWN);
  }

  @Test
  void detects_the_current_machine() {
    var platform = new SonarLintHostPlatform(System2.INSTANCE);

    assertThat(platform.getOs()).isNotNull();
    assertThat(platform.getArch()).isNotNull();
  }

  @Test
  void detects_windows_from_the_declared_architecture() {
    var system = system("Windows 11", "amd64", true);

    var platform = new SonarLintHostPlatform(system);

    assertThat(platform.getOs()).isEqualTo(Os.WINDOWS);
    assertThat(platform.getArch()).isEqualTo(Arch.X64);
  }

  @Test
  void reports_a_missing_os_name_as_unknown() {
    var system = system(null, "amd64", true);

    var platform = new SonarLintHostPlatform(system);

    assertThat(platform.getOs()).isEqualTo(Os.UNKNOWN);
    assertThat(platform.getArch()).isEqualTo(Arch.X64);
  }

  @Test
  void reads_os_release_from_the_primary_file_then_the_fallback(@TempDir Path dir) throws IOException {
    var primary = dir.resolve("os-release");
    var fallback = dir.resolve("fallback");
    Files.writeString(primary, "ID=debian\n");
    Files.writeString(fallback, "ID=alpine\n");

    assertThat(SonarLintHostPlatform.readOsReleaseLines(primary, dir.resolve("unused"))).containsExactly("ID=debian");
    assertThat(SonarLintHostPlatform.readOsReleaseLines(dir.resolve("missing"), fallback)).containsExactly("ID=alpine");
    assertThat(SonarLintHostPlatform.readOsReleaseLines(dir.resolve("missing"), dir.resolve("also-missing"))).isEmpty();
  }

  @Test
  void prefers_uname_and_falls_back_to_the_declared_architecture() {
    var unix = mock(System2.class);
    when(unix.isOsWindows()).thenReturn(false);
    var windows = mock(System2.class);
    when(windows.isOsWindows()).thenReturn(true);
    when(windows.property("os.arch")).thenReturn("amd64");

    assertThat(SonarLintHostPlatform.readRawArch(unix, () -> "aarch64")).isEqualTo("aarch64");
    when(unix.property("os.arch")).thenReturn("amd64");
    assertThat(SonarLintHostPlatform.readRawArch(unix, () -> null)).isEqualTo("amd64");
    assertThat(SonarLintHostPlatform.readRawArch(windows, () -> {
      throw new AssertionError("uname should not run on Windows");
    })).isEqualTo("amd64");
  }

  @Test
  void resolves_an_absolute_uname_executable(@TempDir Path dir) throws IOException {
    var missing = dir.resolve("missing");
    var uname = dir.resolve("uname");
    Files.writeString(uname, "");
    assertThat(uname.toFile().setExecutable(true)).isTrue();

    assertThat(SonarLintHostPlatform.resolveUname(List.of(missing.toString(), uname.toString()))).isEqualTo(uname.toString());
    assertThat(SonarLintHostPlatform.resolveUname(List.of(missing.toString()))).isNull();
    assertThat(SonarLintHostPlatform.tryUname((String) null)).isNull();
  }

  @Test
  void reads_the_architecture_from_uname() {
    assertThat(SonarLintHostPlatform.tryUname(() -> process(true, 0, "aarch64\n"))).isEqualTo("aarch64");
    assertThat(SonarLintHostPlatform.tryUname(() -> process(false, 0, "aarch64\n"))).isNull();
    assertThat(SonarLintHostPlatform.tryUname(() -> process(true, 1, "aarch64\n"))).isNull();
    assertThat(SonarLintHostPlatform.tryUname(() -> process(true, 0, " \n"))).isNull();
  }

  @Test
  void uname_failure_returns_no_architecture() throws Exception {
    var interrupted = mock(Process.class);
    when(interrupted.waitFor(2, TimeUnit.SECONDS)).thenThrow(new InterruptedException("stop"));
    try {
      assertThat(SonarLintHostPlatform.tryUname(() -> interrupted)).isNull();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
    verify(interrupted).destroy();

    var unreadable = mock(Process.class);
    when(unreadable.waitFor(2, TimeUnit.SECONDS)).thenReturn(true);
    when(unreadable.getInputStream()).thenReturn(new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("unreadable");
      }
    });
    assertThat(SonarLintHostPlatform.tryUname(() -> unreadable)).isNull();
    verify(unreadable).destroy();

    assertThat(SonarLintHostPlatform.tryUname(() -> {
      throw new IOException("missing");
    })).isNull();
  }

  private static System2 system(String osName, String arch, boolean windows) {
    var system = mock(System2.class);
    when(system.property("os.name")).thenReturn(osName);
    when(system.property("os.arch")).thenReturn(arch);
    when(system.isOsWindows()).thenReturn(windows);
    return system;
  }

  private static Process process(boolean finished, int exitCode, String output) {
    var process = mock(Process.class);
    try {
      when(process.waitFor(2, TimeUnit.SECONDS)).thenReturn(finished);
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
    when(process.exitValue()).thenReturn(exitCode);
    when(process.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
    return process;
  }

}
