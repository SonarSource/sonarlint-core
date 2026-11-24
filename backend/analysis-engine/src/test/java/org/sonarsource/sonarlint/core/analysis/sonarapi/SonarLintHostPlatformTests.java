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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.api.scanner.Arch;
import org.sonar.api.scanner.Os;
import org.sonar.api.utils.System2;

import static org.assertj.core.api.Assertions.assertThat;

class SonarLintHostPlatformTests {

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

}
