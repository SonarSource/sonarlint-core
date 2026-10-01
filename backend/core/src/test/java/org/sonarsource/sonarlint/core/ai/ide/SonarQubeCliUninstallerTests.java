/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.ai.ide;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliInstallationStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.UninstallCliResponse.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Timeout(15)
class SonarQubeCliUninstallerTests {
  private static final String BLOCK = "# Added by sonarqube-cli installer\nexport PATH=\"$HOME/.local/share/sonarqube-cli/bin:$PATH\"\n";
  @TempDir
  Path home;
  private final SonarQubeCliLocator locator = mock(SonarQubeCliLocator.class);
  private final CliResetRunner runner = mock(CliResetRunner.class);

  @BeforeEach
  void canonicalize_temporary_home() throws IOException {
    home = home.toRealPath();
  }

  @Test
  void removes_executable_empty_installer_directories_and_exact_profile_blocks_after_reset() throws Exception {
    var cli = install(false);
    var profile = Files.writeString(home.resolve(".profile"), "user configuration\n\n" + BLOCK);
    var sonarData = Files.createDirectories(home.resolve(".sonar"));
    var settings = Files.writeString(sonarData.resolve("settings"), "preserved");
    successfulResetWithNativeCopy();
    var uninstaller = uninstaller();

    var response = uninstaller.uninstall(new SonarLintCancelMonitor());

    assertThat(response.getStatus()).isEqualTo(Status.UNINSTALLED);
    assertThat(response.getExecutablePath()).isEqualTo(cli.toString());
    assertThat(response.getResetExitCode()).isZero();
    assertThat(response.getDiagnostics()).anyMatch(message -> message.contains("Review reset stdout and stderr"));
    assertThat(cli).doesNotExist();
    assertThat(cli.getParent().getParent()).doesNotExist();
    assertThat(home.resolve(".local/share")).exists();
    assertThat(profile).hasContent("user configuration\n\n");
    assertThat(settings).hasContent("preserved");
    verify(runner).run(eq(List.of(cli.toString(), "system", "reset", "--force")), eq(Map.of()), eq(Duration.ofSeconds(120)), any(SonarLintCancelMonitor.class));
  }

  @Test
  void exit_zero_warnings_and_localized_text_allow_removal_without_parsing_reset_output() throws Exception {
    var cli = install(false);
    when(runner.run(anyList(), anyMap(), any(), any())).thenReturn(
      new CliResetRunner.Result(0, " warning: failed integration cleanup\n", "avertissement: échec\n", List.of(), false, true));

    var response = uninstaller().uninstall(new SonarLintCancelMonitor());

    assertThat(response.getStatus()).isEqualTo(Status.UNINSTALLED);
    assertThat(response.getStdout()).isEqualTo(" warning: failed integration cleanup\n");
    assertThat(response.getStderr()).isEqualTo("avertissement: échec\n");
    assertThat(cli).doesNotExist();
  }

  @Test
  void retains_executable_and_profile_when_reset_is_unsupported_nonzero_or_cannot_start() throws Exception {
    var cli = install(false);
    var profile = Files.writeString(home.resolve(".profile"), BLOCK);
    var uninstaller = uninstaller();
    for (var result : List.of(new CliResetRunner.Result(2, "", "Unsupported command", List.of(), false, true),
      new CliResetRunner.Result(null, "", "", List.of("Could not start"), true, true),
      new CliResetRunner.Result(null, "", "", List.of("Timed out"), true, true))) {
      when(runner.run(anyList(), anyMap(), any(), any())).thenReturn(result);

      var response = uninstaller.uninstall(new SonarLintCancelMonitor());

      assertThat(response.getStatus()).isEqualTo(Status.RESET_FAILED);
      assertThat(cli).exists();
      assertThat(profile).hasContent(BLOCK);
      assertThat(uninstaller.isAvailable(lookup(cli))).isTrue();
    }
  }

  @Test
  void unavailable_after_unconfirmed_process_shutdown_until_backend_restart() throws Exception {
    var cli = install(false);
    when(runner.run(anyList(), anyMap(), any(), any())).thenReturn(
      new CliResetRunner.Result(null, "", "", List.of("Restart the backend"), true, false));
    var uninstaller = uninstaller();

    assertThat(uninstaller.uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.RESET_FAILED);
    assertThat(uninstaller.isAvailable(lookup(cli))).isFalse();
    assertThat(uninstaller.uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.NOT_AVAILABLE);
    assertThat(cli).exists();
  }

  @Test
  void file_lock_retains_executable_and_path_configuration() throws Exception {
    var cli = install(false);
    var profile = Files.writeString(home.resolve(".profile"), BLOCK);
    successfulResetWithNativeCopy();
    var uninstaller = new SonarQubeCliUninstaller(locator, home, Map.of(), runner, path -> { throw new AccessDeniedException(path.toString()); });

    var response = uninstaller.uninstall(new SonarLintCancelMonitor());

    assertThat(response.getStatus()).isEqualTo(Status.EXECUTABLE_REMOVAL_FAILED);
    assertThat(cli).exists();
    assertThat(profile).hasContent(BLOCK);
  }

  @Test
  void preserves_replacement_executable_and_path_after_reset() throws Exception {
    var cli = install(false);
    var profile = Files.writeString(home.resolve(".profile"), BLOCK);
    when(runner.run(anyList(), anyMap(), any(), any())).thenAnswer(invocation -> {
      var replacement = Files.writeString(home.resolve("replacement"), "replacement binary");
      replacement.toFile().setExecutable(true);
      Files.move(replacement, cli, StandardCopyOption.REPLACE_EXISTING);
      return completedReset();
    });

    var response = uninstaller().uninstall(new SonarLintCancelMonitor());

    assertThat(response.getStatus()).isEqualTo(Status.EXECUTABLE_REMOVAL_FAILED);
    assertThat(cli).hasContent("replacement binary");
    assertThat(profile).hasContent(BLOCK);
  }

  @Test
  void missing_executable_after_reset_retains_path_for_manual_cleanup() throws Exception {
    var cli = install(false);
    var profile = Files.writeString(home.resolve(".profile"), BLOCK);
    when(runner.run(anyList(), anyMap(), any(), any())).thenAnswer(invocation -> {
      Files.delete(cli);
      return completedReset();
    });

    assertThat(uninstaller().uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.EXECUTABLE_REMOVAL_FAILED);
    assertThat(profile).hasContent(BLOCK);
  }

  @Test
  void preserves_unknown_files_without_recursive_directory_removal() throws Exception {
    var cli = install(false);
    var sentinel = Files.writeString(cli.getParent().resolve("unknown"), "preserve");
    var parentSentinel = Files.writeString(cli.getParent().getParent().resolve("another"), "also preserve");
    successfulResetWithNativeCopy();

    var response = uninstaller().uninstall(new SonarLintCancelMonitor());

    assertThat(response.getStatus()).isEqualTo(Status.UNINSTALLED_WITH_REMAINING_CONFIGURATION);
    assertThat(sentinel).hasContent("preserve");
    assertThat(parentSentinel).hasContent("also preserve");
    assertThat(cli).doesNotExist();
  }

  @Test
  void permits_only_the_selected_usable_exact_standard_executable() throws Exception {
    var cli = install(false);
    var uninstaller = uninstaller();
    var custom = Files.writeString(home.resolve("custom-sonar"), "custom");
    custom.toFile().setExecutable(true);

    assertThat(uninstaller.isAvailable(lookup(cli))).isTrue();
    assertThat(uninstaller.isAvailable(lookup(custom))).isFalse();
    assertThat(uninstaller.isAvailable(new SonarQubeCliLocator.CliLookup(CliInstallationStatus.UNUSABLE, cli, null))).isFalse();
    assertThat(uninstaller.isAvailable(new SonarQubeCliLocator.CliLookup(CliInstallationStatus.NOT_INSTALLED, null, null))).isFalse();
    when(locator.find()).thenReturn(lookup(custom));
    assertThat(uninstaller.uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.NOT_AVAILABLE);
    assertThat(cli).exists();
    verify(runner, never()).run(anyList(), anyMap(), any(), any());
  }

  @Test
  void windows_requires_absolute_local_app_data_case_insensitive_key_and_exe_name() throws Exception {
    var cli = install(true);
    var uninstaller = new SonarQubeCliUninstaller(locator, home, Map.of("localAppData", home.toString()), runner, Files::delete);

    assertThat(uninstaller.isAvailable(lookup(cli))).isTrue();
    assertThat(uninstaller.isAvailable(lookup(Files.writeString(cli.getParent().resolve("sonar.cmd"), "wrapper")))).isFalse();
    for (var value : List.of("relative-root", "", "\u0000")) {
      assertThat(new SonarQubeCliUninstaller(locator, home, Map.of("LOCALAPPDATA", value), runner, Files::delete).isAvailable(lookup(cli))).isFalse();
    }
    assertThat(new SonarQubeCliUninstaller(locator, home, Map.of(), runner, Files::delete).isAvailable(lookup(cli))).isFalse();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void rejects_symlink_executable_and_redirected_installer_directory() throws Exception {
    var cli = install(false);
    var target = Files.writeString(home.resolve("external"), "external binary");
    target.toFile().setExecutable(true);
    Files.delete(cli);
    Files.createSymbolicLink(cli, target);
    assertThat(uninstaller().isAvailable(lookup(cli))).isFalse();
    Files.delete(cli);
    Files.delete(cli.getParent());
    var redirected = Files.createDirectories(home.resolve("external-bin"));
    Files.writeString(redirected.resolve("sonar"), "external binary").toFile().setExecutable(true);
    Files.createSymbolicLink(cli.getParent(), redirected);
    assertThat(uninstaller().isAvailable(lookup(cli))).isFalse();
  }

  @Test
  void rejects_shell_wrappers_even_when_they_are_placed_at_the_official_path() throws Exception {
    var cli = install(false);
    Files.writeString(cli, "#!/bin/sh\nexec /other/sonar\n");

    assertThat(uninstaller().isAvailable(lookup(cli))).isFalse();
    assertThat(uninstaller().uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.NOT_AVAILABLE);
    verify(runner, never()).run(anyList(), anyMap(), any(), any());
  }

  @Test
  void rejects_empty_text_and_truncated_native_headers_even_when_discovery_reports_a_usable_cli() throws Exception {
    var cli = install(false);
    var uninstaller = uninstaller();
    for (var contents : List.of(new byte[0], "echo wrapper".getBytes(java.nio.charset.StandardCharsets.UTF_8),
      new byte[] {0x7F, 'E', 'L', 'F'}, nativeHeader(true))) {
      Files.write(cli, contents);
      assertThat(uninstaller.isAvailable(lookup(cli))).isFalse();
      assertThat(uninstaller.uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.NOT_AVAILABLE);
    }
    verify(runner, never()).run(anyList(), anyMap(), any(), any());
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void rejects_a_usable_no_shebang_wrapper_that_java_executes_through_its_shell_fallback() throws Exception {
    var cli = install(false);
    Files.writeString(cli, "printf 'SonarQube CLI 1.9.0\\n'\n");
    var realLocator = new SonarQubeCliLocator(new OsExecutableSearch(mock(System2.class), CommandExecutor.create(),
      Map.of("PATH", ""), home.resolve("unused-path-helper")), home);
    var discovered = realLocator.find();
    assertThat(discovered.installationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    var uninstaller = new SonarQubeCliUninstaller(realLocator, home, Map.of(), runner, Files::delete);

    assertThat(uninstaller.isAvailable(discovered)).isFalse();
    assertThat(uninstaller.uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.NOT_AVAILABLE);
    assertThat(cli).exists();
    verify(runner, never()).run(anyList(), anyMap(), any(), any());
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void rejects_an_elf_prefix_script_that_java_executes_through_its_shell_fallback() throws Exception {
    var cli = install(false);
    var script = new java.io.ByteArrayOutputStream();
    script.write(new byte[] {0x7F, 'E', 'L', 'F', 2, 1, 1, '\n'});
    script.write(("printf 'SonarQube CLI 1.9.0\\n'\n#" + " ".repeat(128) + "\n")
      .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    Files.write(cli, script.toByteArray());
    var realLocator = new SonarQubeCliLocator(new OsExecutableSearch(mock(System2.class), CommandExecutor.create(),
      Map.of("PATH", ""), home.resolve("unused-path-helper")), home);
    var discovered = realLocator.find();
    assertThat(discovered.installationStatus()).isEqualTo(CliInstallationStatus.INSTALLED);
    var uninstaller = new SonarQubeCliUninstaller(realLocator, home, Map.of(), runner, Files::delete);

    assertThat(uninstaller.isAvailable(discovered)).isFalse();
    assertThat(uninstaller.uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.NOT_AVAILABLE);
    assertThat(cli).exists();
    verify(runner, never()).run(anyList(), anyMap(), any(), any());
  }

  @Test
  void elf_requires_a_host_executable_with_bounded_load_segments_and_an_executable_entry_point() throws Exception {
    var cli = install(false);
    var uninstaller = uninstaller();
    assertThat(uninstaller.isAvailable(lookup(cli))).isTrue();
    List<java.util.function.Consumer<ByteBuffer>> invalidHeaders = List.of(
      header -> header.putShort(16, (short) 1), // A relocatable object cannot be executed.
      header -> header.putShort(18, (short) 3), // The official CLI has no ELF32/i386 distribution.
      header -> header.putShort(18, (short) (header.getShort(18) == 62 ? 183 : 62)), // Another host architecture.
      header -> header.putInt(20, 2),
      header -> header.putShort(52, (short) 63),
      header -> header.putShort(54, (short) 55),
      header -> header.putShort(56, (short) 0),
      header -> header.putShort(56, (short) 2000),
      header -> header.putLong(32, Long.MAX_VALUE),
      header -> header.putLong(32, 120), // Program header runs past EOF.
      header -> header.putInt(64, 0), // No loadable program segment.
      header -> header.putInt(68, 4), // Entry is in a non-executable segment.
      header -> header.putLong(96, 129), // Segment bytes run past EOF.
      header -> header.putLong(104, 127), // Memory cannot contain the file-backed segment.
      header -> header.putLong(24, 0x400080L), // Entry lies beyond its file-backed code.
      header -> header.putLong(80, 0x400001L), // File/virtual page offsets differ.
      header -> header.putLong(112, 3)); // ELF segment alignment must be a power of two.
    for (var invalidate : invalidHeaders) {
      var header = ByteBuffer.wrap(nativeHeader(false)).order(ByteOrder.LITTLE_ENDIAN);
      invalidate.accept(header);
      Files.write(cli, header.array());
      assertThat(uninstaller.isAvailable(lookup(cli))).isFalse();
    }
    var pie = ByteBuffer.wrap(nativeHeader(false)).order(ByteOrder.LITTLE_ENDIAN).putShort(16, (short) 3);
    Files.write(cli, pie.array());
    assertThat(uninstaller.isAvailable(lookup(cli))).isTrue();
  }

  @Test
  void elf_requires_a_bounded_absolute_null_terminated_interpreter() throws Exception {
    var cli = install(false);
    var uninstaller = uninstaller();
    var executable = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
    executable.put(nativeHeader(false)).putShort(56, (short) 2).putLong(96, 512).putLong(104, 512);
    executable.putInt(120, 3).putInt(124, 4).putLong(128, 256).putLong(152, 10);
    executable.position(256);
    executable.put(new byte[] {'/', 'l', 'i', 'b', '/', 'l', 'd', '.', 's', 0});
    Files.write(cli, executable.array());
    assertThat(uninstaller.isAvailable(lookup(cli))).isTrue();
    executable.put(256, (byte) 'r');
    Files.write(cli, executable.array());
    assertThat(uninstaller.isAvailable(lookup(cli))).isFalse();
    executable.put(256, (byte) '/').put(265, (byte) 'x');
    Files.write(cli, executable.array());
    assertThat(uninstaller.isAvailable(lookup(cli))).isFalse();
    executable.put(265, (byte) 0).putLong(152, 4097);
    Files.write(cli, executable.array());
    assertThat(uninstaller.isAvailable(lookup(cli))).isFalse();
  }

  @Test
  void windows_requires_a_pe_signature_after_the_dos_header() throws Exception {
    var cli = install(true);
    var uninstaller = new SonarQubeCliUninstaller(locator, home, Map.of("LOCALAPPDATA", home.toString()), runner, Files::delete);
    assertThat(uninstaller.isAvailable(lookup(cli))).isTrue();
    var dosOnly = nativeHeader(true);
    java.util.Arrays.fill(dosOnly, 64, dosOnly.length, (byte) 0);
    for (var contents : List.of(dosOnly, nativeHeader(false), "@echo wrapper".getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
      Files.write(cli, contents);
      assertThat(uninstaller.isAvailable(lookup(cli))).isFalse();
    }
  }

  @Test
  void mac_accepts_native_macho_and_universal_headers_but_rejects_elf_and_java_class_headers() throws Exception {
    when(locator.isMac()).thenReturn(true);
    var cli = install(false);
    var uninstaller = uninstaller();
    for (var magic : new int[] {0xFEEDFACE, 0xFEEDFACF, 0xCEFAEDFE, 0xCFFAEDFE, 0xCAFEBABE, 0xCAFEBABF, 0xBEBAFECA, 0xBFBAFECA}) {
      var header = ByteBuffer.allocate(128).putInt(0, magic);
      var littleEndian = magic == 0xCEFAEDFE || magic == 0xCFFAEDFE || magic == 0xBEBAFECA || magic == 0xBFBAFECA;
      header.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).putInt(4, 2).putInt(12, 2);
      Files.write(cli, header.array());
      assertThat(uninstaller.isAvailable(lookup(cli))).as("Mach-O magic %08x", magic).isTrue();
    }
    var javaClass = ByteBuffer.allocate(128).putInt(0, 0xCAFEBABE).putInt(4, 61).array();
    for (var contents : List.of(javaClass, nativeHeader(false), nativeHeader(true))) {
      Files.write(cli, contents);
      assertThat(uninstaller.isAvailable(lookup(cli))).isFalse();
    }
  }

  @Test
  void uncertain_unix_helper_shutdown_disables_uninstall_after_a_temporary_reinstall() throws Exception {
    assertHelperShutdownAfterReinstall(false, false);
  }

  @Test
  void uncertain_windows_helper_shutdown_disables_uninstall_after_a_temporary_reinstall() throws Exception {
    assertHelperShutdownAfterReinstall(true, false);
  }

  @Test
  void confirmed_unix_helper_failure_remains_retryable_after_a_temporary_reinstall() throws Exception {
    assertHelperShutdownAfterReinstall(false, true);
  }

  @Test
  void confirmed_windows_helper_failure_remains_retryable_after_a_temporary_reinstall() throws Exception {
    assertHelperShutdownAfterReinstall(true, true);
  }

  private void assertHelperShutdownAfterReinstall(boolean windows, boolean shutdownConfirmed) throws Exception {
    var cli = install(windows);
    Files.writeString(home.resolve(".profile"), BLOCK);
    var environment = Map.<String, String>of();
    if (windows) {
      var powershell = home.resolve("windows/System32/WindowsPowerShell/v1.0/powershell.exe");
      Files.createDirectories(powershell.getParent());
      Files.writeString(powershell, "mock process only");
      environment = Map.of("LOCALAPPDATA", home.toString(), "SystemRoot", home.resolve("windows").toString());
    }
    var resets = new AtomicInteger();
    var helpers = new AtomicInteger();
    when(runner.run(anyList(), anyMap(), any(), any())).thenAnswer(invocation -> {
      List<String> command = invocation.getArgument(0);
      if (command.getFirst().equals(cli.toString())) {
        resets.incrementAndGet();
        return completedReset();
      }
      helpers.incrementAndGet();
      return new CliResetRunner.Result(1, "", "", List.of("helper failure"), true, shutdownConfirmed);
    });
    var uninstaller = new SonarQubeCliUninstaller(locator, home, environment, runner, Files::delete);

    assertThat(uninstaller.uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.UNINSTALLED_WITH_REMAINING_CONFIGURATION);
    var reinstalled = install(windows);
    assertThat(uninstaller.isAvailable(lookup(reinstalled))).isEqualTo(shutdownConfirmed);
    var second = uninstaller.uninstall(new SonarLintCancelMonitor());
    assertThat(second.getStatus()).isEqualTo(shutdownConfirmed ? Status.UNINSTALLED_WITH_REMAINING_CONFIGURATION : Status.NOT_AVAILABLE);
    assertThat(resets).hasValue(shutdownConfirmed ? 2 : 1);
    assertThat(helpers).hasValue(shutdownConfirmed ? 2 : 1);
  }

  @Test
  void revalidates_executable_identity_immediately_before_reset() throws Exception {
    var cli = install(false);
    var monitor = new SonarLintCancelMonitor() {
      private int checkpoints;

      @Override
      public void checkCanceled() {
        if (++checkpoints == 2) {
          try {
            Files.writeString(cli, "changed before reset");
          } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
          }
        }
        super.checkCanceled();
      }
    };

    assertThat(uninstaller().uninstall(monitor).getStatus()).isEqualTo(Status.NOT_AVAILABLE);
    assertThat(cli).hasContent("changed before reset");
    verify(runner, never()).run(anyList(), anyMap(), any(), any());
  }

  @Test
  void rediscovery_rejects_a_new_path_candidate_before_reset() throws Exception {
    var cli = install(false);
    var custom = Files.writeString(home.resolve("custom-sonar"), "custom");
    when(locator.find()).thenReturn(lookup(cli), lookup(custom));

    assertThat(uninstaller().uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.NOT_AVAILABLE);
    assertThat(cli).exists();
    verify(runner, never()).run(anyList(), anyMap(), any(), any());
  }

  @Test
  void rediscovery_retains_executable_and_path_when_selection_changes_after_reset() throws Exception {
    var cli = install(false);
    var custom = Files.writeString(home.resolve("custom-sonar"), "custom");
    var profile = Files.writeString(home.resolve(".profile"), BLOCK);
    when(locator.find()).thenReturn(lookup(cli), lookup(cli), lookup(custom));
    successfulResetWithNativeCopy();

    assertThat(uninstaller().uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.EXECUTABLE_REMOVAL_FAILED);
    assertThat(cli).exists();
    assertThat(profile).hasContent(BLOCK);
  }

  @Test
  void cancellation_during_final_rediscovery_retains_executable_and_path() throws Exception {
    var cli = install(false);
    var profile = Files.writeString(home.resolve(".profile"), BLOCK);
    var monitor = new SonarLintCancelMonitor();
    var lookups = new AtomicInteger();
    when(locator.find()).thenAnswer(invocation -> {
      if (lookups.incrementAndGet() == 3) {
        monitor.cancel();
      }
      return lookup(cli);
    });
    successfulResetWithNativeCopy();

    assertThatThrownBy(() -> uninstaller().uninstall(monitor)).isInstanceOf(CancellationException.class);
    assertThat(cli).exists();
    assertThat(profile).hasContent(BLOCK);
  }

  @Test
  void preserves_an_installer_directory_replaced_after_executable_deletion() throws Exception {
    var cli = install(false);
    successfulResetWithNativeCopy();
    var bin = cli.getParent();
    var uninstaller = new SonarQubeCliUninstaller(locator, home, Map.of(), runner, path -> {
      Files.delete(path);
      if (path.equals(cli)) {
        Files.move(bin, home.resolve("original-bin"));
        Files.createDirectory(bin);
      }
    });

    assertThat(uninstaller.uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.UNINSTALLED_WITH_REMAINING_CONFIGURATION);
    assertThat(bin).isDirectory();
  }

  @Test
  void cancellation_after_uncertain_shutdown_latches_uninstall_unavailable() throws Exception {
    var cli = install(false);
    var monitor = new SonarLintCancelMonitor();
    when(runner.run(anyList(), anyMap(), any(), any())).thenAnswer(invocation -> {
      monitor.cancel();
      return new CliResetRunner.Result(null, "", "", List.of("uncertain"), true, false);
    });
    var uninstaller = uninstaller();

    assertThatThrownBy(() -> uninstaller.uninstall(monitor)).isInstanceOf(CancellationException.class);
    assertThat(uninstaller.isAvailable(lookup(cli))).isFalse();
    assertThat(uninstaller.uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.NOT_AVAILABLE);
  }

  @Test
  void malformed_discovery_environment_is_a_safe_rejection() {
    when(locator.find()).thenThrow(new java.nio.file.InvalidPathException("invalid", "malformed root"));

    assertThat(uninstaller().uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.NOT_AVAILABLE);
    verify(runner, never()).run(anyList(), anyMap(), any(), any());
  }

  @Test
  void serializes_overlapping_requests_without_waiting_or_running_reset_twice() throws Exception {
    install(false);
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var resets = new AtomicInteger();
    when(runner.run(anyList(), anyMap(), any(), any())).thenAnswer(invocation -> {
      resets.incrementAndGet();
      started.countDown();
      assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
      return completedReset();
    });
    var uninstaller = uninstaller();
    var operation = CompletableFuture.supplyAsync(() -> uninstaller.uninstall(new SonarLintCancelMonitor()));
    try {
      assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(uninstaller.uninstall(new SonarLintCancelMonitor()).getStatus()).isEqualTo(Status.IN_PROGRESS);
      assertThat(resets).hasValue(1);
    } finally {
      release.countDown();
    }
    assertThat(operation.get(3, TimeUnit.SECONDS).getStatus()).isEqualTo(Status.UNINSTALLED);
  }

  @Test
  void propagates_cancellation_after_process_shutdown_and_releases_guard() throws Exception {
    var cli = install(false);
    var monitor = new SonarLintCancelMonitor();
    when(runner.run(anyList(), anyMap(), any(), any())).thenAnswer(invocation -> {
      monitor.cancel();
      return new CliResetRunner.Result(null, "", "", List.of("cancelled"), true, true);
    });
    var uninstaller = uninstaller();

    assertThatThrownBy(() -> uninstaller.uninstall(monitor)).isInstanceOf(CancellationException.class);
    assertThat(cli).exists();
    assertThat(uninstaller.isAvailable(lookup(cli))).isTrue();
  }

  @Test
  void cancellation_before_reset_does_not_execute_any_process() throws Exception {
    var cli = install(false);
    var monitor = new SonarLintCancelMonitor();
    monitor.cancel();

    assertThatThrownBy(() -> uninstaller().uninstall(monitor)).isInstanceOf(CancellationException.class);
    assertThat(cli).exists();
    verify(runner, never()).run(anyList(), anyMap(), any(), any());
  }

  @Test
  void finishes_installer_cleanup_when_cancellation_arrives_after_executable_deletion_begins() throws Exception {
    var cli = install(false);
    var profile = Files.writeString(home.resolve(".profile"), BLOCK);
    successfulResetWithNativeCopy();
    var monitor = new SonarLintCancelMonitor();
    var uninstaller = new SonarQubeCliUninstaller(locator, home, Map.of(), runner, path -> {
      if (path.equals(cli)) { monitor.cancel(); }
      Files.delete(path);
    });

    var response = uninstaller.uninstall(monitor);

    assertThat(response.getStatus()).isEqualTo(Status.UNINSTALLED);
    assertThat(profile).hasContent("");
  }

  @Test
  void profile_inspection_failure_does_not_prevent_reset_and_removal() throws Exception {
    var cli = install(false);
    Files.createDirectory(home.resolve(".profile"));
    successfulResetWithNativeCopy();

    var response = uninstaller().uninstall(new SonarLintCancelMonitor());

    assertThat(response.getStatus()).isEqualTo(Status.UNINSTALLED_WITH_REMAINING_CONFIGURATION);
    assertThat(response.getDiagnostics()).anyMatch(message -> message.contains("inspect shell profile"));
    assertThat(cli).doesNotExist();
  }

  @Test
  void profile_change_during_reset_is_preserved_and_reported() throws Exception {
    var cli = install(false);
    var profile = Files.writeString(home.resolve(".profile"), BLOCK);
    when(runner.run(anyList(), anyMap(), any(), any())).thenAnswer(invocation -> {
      Files.writeString(profile, "concurrent user edit\n" + BLOCK);
      return completedReset();
    });

    var response = uninstaller().uninstall(new SonarLintCancelMonitor());

    assertThat(response.getStatus()).isEqualTo(Status.UNINSTALLED_WITH_REMAINING_CONFIGURATION);
    assertThat(profile).hasContent("concurrent user edit\n" + BLOCK);
    assertThat(cli).doesNotExist();
  }

  private SonarQubeCliUninstaller uninstaller() {
    return new SonarQubeCliUninstaller(locator, home, Map.of(), runner, Files::delete);
  }

  private Path install(boolean windows) throws IOException {
    when(locator.isWindows()).thenReturn(windows);
    var cli = home.resolve(windows ? "sonarqube-cli/bin/sonar.exe" : ".local/share/sonarqube-cli/bin/sonar");
    Files.createDirectories(cli.getParent());
    Files.write(cli, nativeHeader(windows));
    assertThat(cli.toFile().setExecutable(true)).isTrue();
    when(locator.find()).thenReturn(lookup(cli));
    return cli;
  }

  private static byte[] nativeHeader(boolean windows) {
    var header = ByteBuffer.allocate(128);
    if (windows) {
      header.order(ByteOrder.LITTLE_ENDIAN).putShort(0, (short) 0x5A4D).putInt(60, 64).putInt(64, 0x00004550);
    } else {
      header.order(ByteOrder.LITTLE_ENDIAN).putInt(0, 0x464C457F).put(4, (byte) 2).put(5, (byte) 1).put(6, (byte) 1);
      var machine = System.getProperty("os.arch", "").equals("aarch64") || System.getProperty("os.arch", "").equals("arm64") ? 183 : 62;
      header.putShort(16, (short) 2).putShort(18, (short) machine).putInt(20, 1).putLong(24, 0x400078L).putLong(32, 64);
      header.putShort(52, (short) 64).putShort(54, (short) 56).putShort(56, (short) 1);
      header.putInt(64, 1).putInt(68, 5).putLong(72, 0).putLong(80, 0x400000L).putLong(88, 0x400000L);
      header.putLong(96, 128).putLong(104, 128).putLong(112, 4096);
    }
    return header.array();
  }

  private void successfulResetWithNativeCopy() {
    when(runner.run(anyList(), anyMap(), any(), any())).thenAnswer(invocation -> {
      List<String> command = invocation.getArgument(0);
      if (command.get(0).equals("/bin/cp")) {
        return new CliResetRunner().run(command, Map.of(), Duration.ofSeconds(5), new SonarLintCancelMonitor());
      }
      return completedReset();
    });
  }

  private static CliResetRunner.Result completedReset() {
    return new CliResetRunner.Result(0, "", "", List.of(), false, true);
  }

  private static SonarQubeCliLocator.CliLookup lookup(Path cli) {
    return new SonarQubeCliLocator.CliLookup(CliInstallationStatus.INSTALLED, cli, "1.9.0");
  }
}
