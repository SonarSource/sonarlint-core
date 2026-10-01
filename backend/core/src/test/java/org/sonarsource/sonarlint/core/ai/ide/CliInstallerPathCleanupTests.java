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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CliInstallerPathCleanupTests {
  private static final String BLOCK = "# Added by sonarqube-cli installer\nexport PATH=\"$HOME/.local/share/sonarqube-cli/bin:$PATH\"\n";
  private static final CliResetRunner.Result SUCCESS = new CliResetRunner.Result(0, "", "", List.of(), false, true);

  @TempDir
  Path tempDir;

  private final CliResetRunner runner = mock(CliResetRunner.class);

  @BeforeEach
  void use_the_real_temporary_directory_path() throws IOException {
    // macOS temporary-directory ancestors may themselves be symbolic links.
    tempDir = tempDir.toRealPath();
  }

  @Test
  void snapshot_does_not_modify_profiles_or_run_commands() throws IOException {
    var profile = profile(".bashrc", "before\n" + BLOCK + "after\n");

    var snapshot = cleanup(Map.of()).snapshot(binDirectory());

    assertThat(snapshot.diagnostics()).isEmpty();
    assertThat(Files.readString(profile)).isEqualTo("before\n" + BLOCK + "after\n");
    verifyNoInteractions(runner);
  }

  @Test
  void removes_only_exact_adjacent_installer_lines_and_preserves_all_other_bytes() throws IOException {
    stubSuccessfulCopy();
    var original = ("\uFEFFbefore\r\n" + BLOCK.replace("\n", "\r\n") + "after\r\n").getBytes(StandardCharsets.UTF_8);
    original[original.length - 3] = (byte) 0xFF;
    var profile = tempDir.resolve(".bashrc");
    Files.write(profile, original);
    var expected = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'b', 'e', 'f', 'o', 'r', 'e', '\r', '\n', 'a', 'f', 't', 'e', (byte) 0xFF, '\r', '\n'};
    var cleanup = cleanup(Map.of());

    assertThat(cleanup.cleanup(cleanup.snapshot(binDirectory())).diagnostics()).isEmpty();

    assertThat(Files.readAllBytes(profile)).containsExactly(expected);
    assertNoStagingDirectories();
  }

  @Test
  void preserves_a_bom_when_the_installer_block_is_the_first_content() throws IOException {
    stubSuccessfulCopy();
    var profile = profile(".profile", "\uFEFF" + BLOCK + "retained");
    var cleanup = cleanup(Map.of());

    assertThat(cleanup.cleanup(cleanup.snapshot(binDirectory())).diagnostics()).isEmpty();

    assertThat(Files.readString(profile)).isEqualTo("\uFEFFretained");
  }

  @Test
  void removes_multiple_blocks_including_an_export_without_a_final_newline() throws IOException {
    stubSuccessfulCopy();
    var profile = profile(".bash_profile", BLOCK + "keep\n" + BLOCK.stripTrailing());
    var cleanup = cleanup(Map.of());

    assertThat(cleanup.cleanup(cleanup.snapshot(binDirectory())).diagnostics()).isEmpty();

    assertThat(Files.readString(profile)).isEqualTo("keep\n");
  }

  @Test
  void preserves_unmarked_or_modified_exports_and_reports_manual_cleanup() throws IOException {
    var original = "# user settings\nexport PATH=\"$HOME/.local/share/sonarqube-cli/bin:$PATH\"\n"
      + "# Added by sonarqube-cli installer\n# keep this comment\nexport PATH=\"$HOME/.local/share/sonarqube-cli/bin:$PATH\"\n"
      + "# Added by sonarqube-cli installer\n export PATH=\"$HOME/.local/share/sonarqube-cli/bin:$PATH\"\n";
    var profile = profile(".zshrc", original);
    var cleanup = cleanup(Map.of());

    var snapshot = cleanup.snapshot(binDirectory());

    assertThat(snapshot.diagnostics()).singleElement().asString().contains("Unrecognized", profile.toString(), "manually");
    assertThat(cleanup.cleanup(snapshot).diagnostics()).isEmpty();
    assertThat(Files.readString(profile)).isEqualTo(original);
    verifyNoInteractions(runner);
  }

  @Test
  void removes_known_blocks_but_reports_remaining_custom_path_configuration() throws IOException {
    stubSuccessfulCopy();
    var custom = "export PATH=/custom/sonarqube-cli/bin:$PATH\n";
    var profile = profile(".profile", BLOCK + custom);
    var cleanup = cleanup(Map.of());

    var snapshot = cleanup.snapshot(binDirectory());

    assertThat(snapshot.diagnostics()).singleElement().asString().contains("Unrecognized");
    assertThat(cleanup.cleanup(snapshot).diagnostics()).isEmpty();
    assertThat(Files.readString(profile)).isEqualTo(custom);
  }

  @Test
  void scans_known_profiles_and_absolute_overrides_without_duplicate_work() throws IOException {
    stubSuccessfulCopy();
    var zshDirectory = Files.createDirectory(tempDir.resolve("zsh"));
    var override = profile("custom-profile", BLOCK);
    for (var name : List.of(".profile", ".bashrc", ".bash_profile", ".zprofile", ".zshrc")) {
      profile(name, BLOCK);
    }
    Files.writeString(zshDirectory.resolve(".zshrc"), BLOCK);
    Files.writeString(zshDirectory.resolve(".zprofile"), BLOCK);
    var cleanup = cleanup(Map.of("ZDOTDIR", zshDirectory.toString(), "PROFILE", override.toString()));

    var snapshot = cleanup.snapshot(binDirectory());
    assertThat(snapshot.profiles()).hasSize(8);
    assertThat(cleanup.cleanup(snapshot).diagnostics()).isEmpty();
    verify(runner, times(8)).run(anyList(), anyMap(), any(), any());

    profile(".zshrc", BLOCK);
    var duplicated = cleanup(Map.of("ZDOTDIR", tempDir.toString(), "PROFILE", tempDir.resolve(".zshrc").toString())).snapshot(binDirectory());
    assertThat(duplicated.profiles()).hasSize(1);
  }

  @Test
  void reports_relative_and_malformed_overrides_without_failing_snapshot() {
    var snapshot = cleanup(Map.of("PROFILE", "relative", "ZDOTDIR", "bad\u0000path")).snapshot(binDirectory());

    assertThat(snapshot.diagnostics()).hasSize(2);
    assertThat(snapshot.diagnostics()).anyMatch(message -> message.contains("PROFILE") && message.contains("relative"));
    assertThat(snapshot.diagnostics()).anyMatch(message -> message.contains("ZDOTDIR") && message.contains("not a valid path"));
  }

  @Test
  void ignores_missing_profiles_and_dev_null_override() {
    var cleanup = cleanup(Map.of("PROFILE", "/dev/null"));
    var snapshot = cleanup.snapshot(binDirectory());

    assertThat(snapshot.profiles()).isEmpty();
    assertThat(snapshot.diagnostics()).isEmpty();
    assertThat(cleanup.cleanup(snapshot).diagnostics()).isEmpty();
    verifyNoInteractions(runner);
  }

  @Test
  void refuses_a_profile_larger_than_the_bounded_size_limit() throws IOException {
    Files.write(tempDir.resolve(".bashrc"), new byte[4 * 1024 * 1024 + 1]);

    var snapshot = cleanup(Map.of()).snapshot(binDirectory());

    assertThat(snapshot.diagnostics()).singleElement().asString().contains("Could not safely inspect");
    assertThat(snapshot.profiles()).isEmpty();
    verifyNoInteractions(runner);
  }

  @Test
  void refuses_non_regular_profiles() throws IOException {
    Files.createDirectory(tempDir.resolve(".profile"));

    var snapshot = cleanup(Map.of()).snapshot(binDirectory());

    assertThat(snapshot.diagnostics()).singleElement().asString().contains("Could not safely inspect");
    assertThat(snapshot.profiles()).isEmpty();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void refuses_symlinked_profiles_and_symlinked_parent_directories() throws IOException {
    var actual = profile("actual", BLOCK);
    Files.createSymbolicLink(tempDir.resolve(".bashrc"), actual);
    var zsh = Files.createDirectory(tempDir.resolve("actual-zsh"));
    Files.writeString(zsh.resolve(".zshrc"), BLOCK);
    var alias = Files.createSymbolicLink(tempDir.resolve("zsh"), zsh);

    var snapshot = cleanup(Map.of("ZDOTDIR", alias.toString())).snapshot(binDirectory());

    assertThat(snapshot.diagnostics()).hasSize(2).allMatch(message -> message.contains("Could not safely inspect"));
    assertThat(snapshot.profiles()).isEmpty();
    assertThat(Files.readString(actual)).isEqualTo(BLOCK);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void refuses_hardlinked_profiles() throws IOException {
    var actual = profile("actual", BLOCK);
    Files.createLink(tempDir.resolve(".profile"), actual);

    var snapshot = cleanup(Map.of()).snapshot(binDirectory());

    assertThat(snapshot.diagnostics()).singleElement().asString().contains("Could not safely inspect");
    assertThat(snapshot.profiles()).isEmpty();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void refuses_special_unix_permission_bits_instead_of_risking_their_loss() throws IOException {
    var profile = profile(".profile", BLOCK);
    Files.setAttribute(profile, "unix:mode", 0104640);

    var snapshot = cleanup(Map.of()).snapshot(binDirectory());

    assertThat(snapshot.diagnostics()).singleElement().asString().contains("Could not safely inspect");
    assertThat(snapshot.profiles()).isEmpty();
    assertThat(Files.readString(profile)).isEqualTo(BLOCK);
  }

  @Test
  void preserves_profiles_changed_after_snapshot() throws IOException {
    var profile = profile(".bashrc", BLOCK);
    var cleanup = cleanup(Map.of());
    var snapshot = cleanup.snapshot(binDirectory());
    Files.writeString(profile, "new settings\n" + BLOCK);

    var diagnostics = cleanup.cleanup(snapshot).diagnostics();

    assertThat(diagnostics).singleElement().asString().contains("Could not safely update");
    assertThat(Files.readString(profile)).isEqualTo("new settings\n" + BLOCK);
    verifyNoInteractions(runner);
  }

  @Test
  void checks_original_profile_bytes_even_when_size_and_timestamp_are_unchanged() throws IOException {
    var profile = profile(".bashrc", BLOCK);
    var cleanup = cleanup(Map.of());
    var snapshot = cleanup.snapshot(binDirectory());
    Files.writeString(profile, BLOCK.replace("Added", "added"));
    Files.setLastModifiedTime(profile, snapshot.profiles().getFirst().attributes().lastModifiedTime());

    assertThat(cleanup.cleanup(snapshot).diagnostics()).singleElement().asString().contains("Could not safely update");

    assertThat(Files.readString(profile)).isEqualTo(BLOCK.replace("Added", "added"));
    verifyNoInteractions(runner);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void preserves_profiles_whose_permissions_changed_after_snapshot() throws IOException {
    var profile = profile(".bashrc", BLOCK);
    var cleanup = cleanup(Map.of());
    var snapshot = cleanup.snapshot(binDirectory());
    Files.setPosixFilePermissions(profile, PosixFilePermissions.fromString("rw-r-----"));

    assertThat(cleanup.cleanup(snapshot).diagnostics()).singleElement().asString().contains("Could not safely update");

    assertThat(Files.getPosixFilePermissions(profile)).isEqualTo(PosixFilePermissions.fromString("rw-r-----"));
    assertThat(Files.readString(profile)).isEqualTo(BLOCK);
    verifyNoInteractions(runner);
  }

  @Test
  void preserves_profiles_replaced_with_identical_content_after_snapshot() throws IOException {
    var profile = profile(".bashrc", BLOCK);
    var cleanup = cleanup(Map.of());
    var snapshot = cleanup.snapshot(binDirectory());
    var replacement = profile("replacement", BLOCK);
    Files.setLastModifiedTime(replacement, snapshot.profiles().getFirst().attributes().lastModifiedTime());
    Files.move(replacement, profile, StandardCopyOption.REPLACE_EXISTING);

    assertThat(cleanup.cleanup(snapshot).diagnostics()).singleElement().asString().contains("Could not safely update");

    assertThat(Files.readString(profile)).isEqualTo(BLOCK);
    verifyNoInteractions(runner);
  }

  @Test
  void preserves_profile_if_native_copy_fails_and_removes_the_staging_directory() throws IOException {
    var profile = profile(".bashrc", BLOCK);
    when(runner.run(anyList(), anyMap(), any(), any()))
      .thenReturn(new CliResetRunner.Result(1, "", "", List.of("copy failed"), false, true));
    var cleanup = cleanup(Map.of());

    assertThat(cleanup.cleanup(cleanup.snapshot(binDirectory())).diagnostics()).contains("copy failed")
      .anyMatch(message -> message.contains("Could not safely update"));

    assertThat(Files.readString(profile)).isEqualTo(BLOCK);
    assertNoStagingDirectories();
  }

  @Test
  void preserves_profile_when_the_native_copy_shutdown_is_uncertain() throws IOException {
    var profile = profile(".bashrc", BLOCK);
    when(runner.run(anyList(), anyMap(), any(), any()))
      .thenReturn(new CliResetRunner.Result(0, "", "", List.of(), false, false));
    var cleanup = cleanup(Map.of());

    var remainingProfile = profile(".zshrc", BLOCK);
    var result = cleanup.cleanup(cleanup.snapshot(binDirectory()));

    assertThat(result.shutdownConfirmed()).isFalse();
    assertThat(result.diagnostics()).anyMatch(message -> message.contains("Could not safely update"))
      .anyMatch(message -> message.contains("Skipped shell profile"))
      .anyMatch(message -> message.contains("Restart the backend"));
    verify(runner, times(1)).run(anyList(), anyMap(), any(), any());
    assertThat(Files.readString(profile)).isEqualTo(BLOCK);
    assertThat(Files.readString(remainingProfile)).isEqualTo(BLOCK);
    assertNoStagingDirectories();
  }

  @Test
  void preserves_profile_when_it_changes_during_the_native_copy() throws IOException {
    var profile = profile(".bashrc", BLOCK);
    doAnswer(invocation -> {
      List<String> command = invocation.getArgument(0);
      Files.copy(Path.of(command.get(command.size() - 2)), Path.of(command.getLast()), StandardCopyOption.COPY_ATTRIBUTES);
      Files.writeString(profile, "changed during copy\n");
      return SUCCESS;
    }).when(runner).run(anyList(), anyMap(), any(), any());
    var cleanup = cleanup(Map.of());

    assertThat(cleanup.cleanup(cleanup.snapshot(binDirectory())).diagnostics()).singleElement().asString().contains("Could not safely update");

    assertThat(Files.readString(profile)).isEqualTo("changed during copy\n");
    assertNoStagingDirectories();
  }

  @Test
  void preserves_profile_if_native_copy_does_not_preserve_content() throws IOException {
    var profile = profile(".bashrc", BLOCK);
    doAnswer(invocation -> {
      List<String> command = invocation.getArgument(0);
      Files.copy(Path.of(command.get(command.size() - 2)), Path.of(command.getLast()), StandardCopyOption.COPY_ATTRIBUTES);
      Files.writeString(Path.of(command.getLast()), "unexpected copy content\n");
      return SUCCESS;
    }).when(runner).run(anyList(), anyMap(), any(), any());
    var cleanup = cleanup(Map.of());

    assertThat(cleanup.cleanup(cleanup.snapshot(binDirectory())).diagnostics()).singleElement().asString().contains("Could not safely update");

    assertThat(Files.readString(profile)).isEqualTo(BLOCK);
    assertNoStagingDirectories();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void preserves_profile_if_native_copy_does_not_preserve_permissions() throws IOException {
    var profile = profile(".bashrc", BLOCK);
    Files.setPosixFilePermissions(profile, PosixFilePermissions.fromString("rw-r-----"));
    doAnswer(invocation -> {
      List<String> command = invocation.getArgument(0);
      Files.copy(Path.of(command.get(command.size() - 2)), Path.of(command.getLast()), StandardCopyOption.COPY_ATTRIBUTES);
      Files.setPosixFilePermissions(Path.of(command.getLast()), PosixFilePermissions.fromString("rw-------"));
      return SUCCESS;
    }).when(runner).run(anyList(), anyMap(), any(), any());
    var cleanup = cleanup(Map.of());

    assertThat(cleanup.cleanup(cleanup.snapshot(binDirectory())).diagnostics()).singleElement().asString().contains("Could not safely update");

    assertThat(Files.readString(profile)).isEqualTo(BLOCK);
    assertThat(Files.getPosixFilePermissions(profile)).isEqualTo(PosixFilePermissions.fromString("rw-r-----"));
    assertNoStagingDirectories();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void native_staging_preserves_posix_metadata_timestamps_and_extended_attributes() throws IOException {
    var profile = profile(".bashrc", "retained\n" + BLOCK);
    Files.setPosixFilePermissions(profile, PosixFilePermissions.fromString("rw-r-----"));
    var timestamp = FileTime.fromMillis(1_600_000_000_000L);
    Files.setLastModifiedTime(profile, timestamp);
    var metadata = Files.readAttributes(profile, PosixFileAttributes.class);
    var extended = Files.getFileAttributeView(profile, UserDefinedFileAttributeView.class);
    if (extended != null) {
      extended.write("sonarqube-cleanup-test", ByteBuffer.wrap(new byte[] {1, 2, 3}));
    }
    var cleanup = new CliInstallerPathCleanup(false, tempDir, Map.of(), new CliResetRunner());

    assertThat(cleanup.cleanup(cleanup.snapshot(binDirectory())).diagnostics()).isEmpty();

    assertThat(Files.readString(profile)).isEqualTo("retained\n");
    var after = Files.readAttributes(profile, PosixFileAttributes.class);
    assertThat(after.owner()).isEqualTo(metadata.owner());
    assertThat(after.group()).isEqualTo(metadata.group());
    assertThat(after.permissions()).isEqualTo(metadata.permissions());
    assertThat(after.lastModifiedTime()).isEqualTo(timestamp);
    if (extended != null) {
      var view = Files.getFileAttributeView(profile, UserDefinedFileAttributeView.class);
      var value = ByteBuffer.allocate(view.size("sonarqube-cleanup-test"));
      view.read("sonarqube-cleanup-test", value);
      assertThat(value.array()).containsExactly(1, 2, 3);
    }
    assertNoStagingDirectories();
  }

  @Test
  void windows_cleanup_uses_a_fixed_script_absolute_system_powershell_and_environment_binding() throws IOException {
    var powershell = systemPowerShell();
    when(runner.run(anyList(), anyMap(), any(), any())).thenReturn(SUCCESS);
    var directory = tempDir.resolve("cli with spaces; literal/bin");
    var cleanup = new CliInstallerPathCleanup(true, tempDir, Map.of("systemroot", powershell.getParent().getParent().getParent().getParent().toString()), runner);

    var snapshot = cleanup.snapshot(directory);
    assertThat(snapshot.diagnostics()).isEmpty();
    assertThat(snapshot.profiles()).isEmpty();
    assertThat(cleanup.cleanup(snapshot).diagnostics()).isEmpty();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> command = ArgumentCaptor.forClass(List.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> environment = ArgumentCaptor.forClass(Map.class);
    verify(runner).run(command.capture(), environment.capture(), any(), any());
    assertThat(command.getValue()).startsWith(powershell.toString(), "-NoLogo", "-NoProfile", "-NonInteractive", "-Command");
    assertThat(command.getValue()).hasSize(6);
    assertThat(command.getValue().getLast()).contains("DoNotExpandEnvironmentNames", "$key.GetValueKind('Path')", "$key.SetValue('Path'", "$kind",
      "OrdinalIgnoreCase", "StringSplitOptions]::None", "$literal.Contains('%')").doesNotContain(directory.toString(), "ExpandEnvironmentVariables");
    assertThat(environment.getValue()).containsExactly(Map.entry("SONARQUBE_CLI_UNINSTALL_BIN", directory.toString()));
  }

  @Test
  void windows_cleanup_accepts_windir_as_a_fallback() throws IOException {
    var powershell = systemPowerShell();
    when(runner.run(anyList(), anyMap(), any(), any())).thenReturn(SUCCESS);
    var cleanup = new CliInstallerPathCleanup(true, tempDir, Map.of("windir", powershell.getParent().getParent().getParent().getParent().toString()), runner);

    assertThat(cleanup.cleanup(cleanup.snapshot(binDirectory())).diagnostics()).isEmpty();

    verify(runner).run(anyList(), anyMap(), any(), any());
  }

  @Test
  void windows_cleanup_reports_missing_relative_or_invalid_system_powershell_without_searching_path() {
    for (var environment : List.of(Map.<String, String>of(), Map.of("SystemRoot", "relative"), Map.of("SystemRoot", "bad\u0000path"),
      Map.of("SystemRoot", tempDir.toString()))) {
      var cleanup = new CliInstallerPathCleanup(true, tempDir, environment, runner);

      assertThat(cleanup.cleanup(cleanup.snapshot(binDirectory())).diagnostics()).singleElement().asString().contains("Could not locate the system PowerShell");
    }
    verifyNoInteractions(runner);
  }

  @Test
  void windows_cleanup_reports_process_failure_and_bounded_runner_diagnostics() throws IOException {
    systemPowerShell();
    when(runner.run(anyList(), anyMap(), any(), any()))
      .thenReturn(new CliResetRunner.Result(null, "", "", List.of("command timed out"), true, true));
    var cleanup = new CliInstallerPathCleanup(true, tempDir, Map.of("SystemRoot", tempDir.resolve("windows").toString()), runner);

    assertThat(cleanup.cleanup(cleanup.snapshot(binDirectory())).diagnostics()).containsExactly("command timed out",
      "Could not update Windows user PATH; remove the SonarQube CLI installation directory manually.");
  }

  @Test
  void confirmed_copy_failure_still_attempts_independent_profiles() throws IOException {
    profile(".bashrc", BLOCK);
    profile(".zshrc", BLOCK);
    when(runner.run(anyList(), anyMap(), any(), any()))
      .thenReturn(new CliResetRunner.Result(1, "", "", List.of("copy failed"), true, true));
    var cleanup = cleanup(Map.of());

    var result = cleanup.cleanup(cleanup.snapshot(binDirectory()));

    assertThat(result.shutdownConfirmed()).isTrue();
    assertThat(result.diagnostics()).noneMatch(message -> message.contains("Skipped shell profile"));
    verify(runner, times(2)).run(anyList(), anyMap(), any(), any());
  }

  @Test
  void windows_cleanup_propagates_uncertain_shutdown() throws IOException {
    systemPowerShell();
    when(runner.run(anyList(), anyMap(), any(), any()))
      .thenReturn(new CliResetRunner.Result(null, "", "", List.of("shutdown uncertain"), true, false));
    var cleanup = new CliInstallerPathCleanup(true, tempDir, Map.of("SystemRoot", tempDir.resolve("windows").toString()), runner);

    var result = cleanup.cleanup(cleanup.snapshot(binDirectory()));

    assertThat(result.shutdownConfirmed()).isFalse();
    assertThat(result.diagnostics()).contains("shutdown uncertain")
      .anyMatch(message -> message.contains("Restart the backend"));
  }

  private CliInstallerPathCleanup cleanup(Map<String, String> environment) {
    assumeFalse(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows"));
    return new CliInstallerPathCleanup(false, tempDir, environment, runner);
  }

  private Path binDirectory() {
    return tempDir.resolve(".local/share/sonarqube-cli/bin");
  }

  private Path profile(String name, String contents) throws IOException {
    return Files.writeString(tempDir.resolve(name), contents);
  }

  private Path systemPowerShell() throws IOException {
    var executable = tempDir.resolve("windows/System32/WindowsPowerShell/v1.0/powershell.exe");
    Files.createDirectories(executable.getParent());
    return Files.writeString(executable, "test fixture");
  }

  private void stubSuccessfulCopy() {
    doAnswer(invocation -> {
      List<String> command = invocation.getArgument(0);
      assertThat(command.getFirst()).isEqualTo("/bin/cp");
      assertThat(command.get(1)).isEqualTo("-pP");
      assertThat((Duration) invocation.getArgument(2)).isEqualTo(Duration.ofSeconds(10));
      Files.copy(Path.of(command.get(command.size() - 2)), Path.of(command.getLast()), StandardCopyOption.COPY_ATTRIBUTES);
      return SUCCESS;
    }).when(runner).run(anyList(), anyMap(), any(), any());
  }

  private void assertNoStagingDirectories() throws IOException {
    try (var files = Files.list(tempDir)) {
      assertThat(files).noneMatch(path -> path.getFileName().toString().startsWith(".sonarqube-cli-uninstall-"));
    }
  }
}
