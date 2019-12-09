/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected;

import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.SonarRuntime;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.Version;
import org.sonarsource.sonarlint.core.container.global.MapSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultServerTest {

  @Test
  public void shouldLoadServerProperties() {
    SonarRuntime runtime = mock(SonarRuntime.class);
    when(runtime.getApiVersion()).thenReturn(Version.create(2, 2));
    Settings settings = new MapSettings();
    settings.setProperty(CoreProperties.SERVER_ID, "123");
    settings.setProperty(CoreProperties.SERVER_STARTTIME, "2010-05-18T17:59:00+0000");
    settings.setProperty(CoreProperties.PERMANENT_SERVER_ID, "abcde");

    DefaultServer metadata = new DefaultServer(settings, runtime);

    assertThat(metadata.getId()).isEqualTo("123");
    assertThat(metadata.getVersion()).isEqualTo("2.2");
    assertThat(metadata.getStartedAt()).isNotNull();
    assertThat(metadata.getURL()).isNull();
    assertThat(metadata.getPermanentServerId()).isEqualTo("abcde");
  }

  @Test
  public void coverageUnusedMethods() {
    SonarRuntime runtime = mock(SonarRuntime.class);
    when(runtime.getApiVersion()).thenReturn(Version.create(2, 2));
    DefaultServer metadata = new DefaultServer(new MapSettings(), runtime);
    assertThat(metadata.getStartedAt()).isNull();
    assertThat(metadata.getRootDir()).isNull();
    assertThat(metadata.getContextPath()).isNull();
    assertThat(metadata.isSecured()).isFalse();
    assertThat(metadata.isDev()).isFalse();
    assertThat(metadata.getPublicRootUrl()).isNull();

  }
}
