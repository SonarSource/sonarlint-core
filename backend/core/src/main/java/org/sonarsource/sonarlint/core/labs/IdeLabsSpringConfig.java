/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.labs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;


@Configuration
@Import({IdeLabsHttpClient.class, IdeLabsService.class})
public class IdeLabsSpringConfig {
  public static final String PROPERTY_IDE_LABS_SUBSCRIPTION_URL = "sonarlint.internal.labs.subscription.url";
  public static final String IDE_LABS_SUBSCRIPTION_URL = "https://discover.sonarsource.com/sq-ide-labs.json";

  @Bean(name = "labsSubscriptionEndpoint")
  String provideLabsSubscriptionEndpoint() {
    return System.getProperty(PROPERTY_IDE_LABS_SUBSCRIPTION_URL, IDE_LABS_SUBSCRIPTION_URL);
  }
}
