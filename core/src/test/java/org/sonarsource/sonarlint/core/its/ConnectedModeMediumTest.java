/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.its;

import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.SonarLintClientImpl;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.LogOutput;
import org.sonarsource.sonarlint.core.client.api.SonarLintClient;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;

public class ConnectedModeMediumTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();
  private static SonarLintClient sonarlint;

  @BeforeClass
  public static void prepare() throws IOException {
    sonarlint = new SonarLintClientImpl(GlobalConfiguration.builder().setVerbose(true).setServerId("dory").setLogOutput(new LogOutput() {

      @Override
      public void log(String formattedMessage, Level level) {
        System.out.println(level + ": " + formattedMessage);
      }
    }).build());
  }

  @Test
  public void validateCredentials() throws Exception {
    ServerConfiguration serverConfig = ServerConfiguration.builder()
      .url("https://dory.sonarsource.com")
      .userAgent("Medium Test")
      .build();
    ValidationResult result = sonarlint.validateCredentials(serverConfig);
    System.out.println(result.status());
    System.out.println(result.statusCode());
    System.out.println(result.message());

  }

  @Test
  public void sync() throws Exception {
    ServerConfiguration serverConfig = ServerConfiguration.builder()
      .url("https://dory.sonarsource.com")
      .userAgent("Medium Test")
      .build();

    sonarlint.sync(serverConfig);

    sonarlint.start();

  }

}
