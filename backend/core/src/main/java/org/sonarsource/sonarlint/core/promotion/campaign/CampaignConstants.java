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
package org.sonarsource.sonarlint.core.promotion.campaign;

public class CampaignConstants {

  static final String FEEDBACK_2025_12_CAMPAIGN = "feedback_2025_12";
  private static final String GOOGLE_FORM = "https://docs.google.com/forms/d/1ch2YxyF3n62JN3eiWHeMQOH6S7R6LHO6JWnLNdPWRYE/preview";
  private static final String JETBRAINS_MARKETPLACE = "https://plugins.jetbrains.com/plugin/7973-sonarqube-for-ide/reviews";
  private static final String VS_MARKETPLACE = "https://marketplace.visualstudio.com/items?itemName=SonarSource.SonarLintforVisualStudio2022&ssr=false#review-details";
  private static final String VSCODE_MARKETPLACE = "https://marketplace.visualstudio.com/items?itemName=SonarSource.sonarlint-vscode&ssr=false#review-details";
  private static final String OPEN_VSX = "https://open-vsx.org/extension/SonarSource/sonarlint-vscode/reviews";

  private CampaignConstants() {
  }

  static String urlToOpen(FeedbackNotificationActionItem response, String productKey) {
    return switch (response) {
      case LOVE_IT -> switch (productKey) {
        case "idea" -> JETBRAINS_MARKETPLACE;
        case "visualstudio" -> VS_MARKETPLACE;
        case "vscode" -> VSCODE_MARKETPLACE;
        case "windsurf", "cursor" -> OPEN_VSX;
        default -> null;
      };
      case SHARE_FEEDBACK -> GOOGLE_FORM;
      default -> null;
    };
  }
}
