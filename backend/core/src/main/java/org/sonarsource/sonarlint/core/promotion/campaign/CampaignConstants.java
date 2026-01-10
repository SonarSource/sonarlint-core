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

  public static final String FEEDBACK_2026_01_CAMPAIGN = "feedback_2026_01";
  private static final String JETBRAINS_MARKETPLACE = "https://plugins.jetbrains.com/plugin/7973-sonarqube-for-ide/reviews";
  private static final String VS_MARKETPLACE = "https://marketplace.visualstudio.com/items?itemName=SonarSource.SonarLintforVisualStudio2022&ssr=false#review-details";
  private static final String VSCODE_MARKETPLACE = "https://marketplace.visualstudio.com/items?itemName=SonarSource.sonarlint-vscode&ssr=false#review-details";
  private static final String OPEN_VSX = "https://open-vsx.org/extension/SonarSource/sonarlint-vscode/reviews";
  private static final String INTELLIJ_GOOGLE_FORM = "https://forms.gle/kDyQ7sDyBfpPEBsy6";
  private static final String VISUAL_STUDIO_GOOGLE_FORM = "https://forms.gle/LjKGKWECDdJw1PmU7";
  private static final String VS_CODE_GOOGLE_FORM = "https://forms.gle/TncKAVK4EWM7z4RV6";

  private CampaignConstants() {
  }

  static String urlToOpen(FeedbackNotificationActionItem response, String productKey) {
    return switch (response) {
      case LOVE_IT -> switch (productKey) {
        case "idea" -> JETBRAINS_MARKETPLACE;
        case "visualstudio" -> VS_MARKETPLACE;
        case "vscode" -> VSCODE_MARKETPLACE;
        case "windsurf", "cursor", "kiro" -> OPEN_VSX;
        default -> null;
      };
      case SHARE_FEEDBACK -> switch (productKey) {
        case "idea" -> INTELLIJ_GOOGLE_FORM;
        case "visualstudio" -> VISUAL_STUDIO_GOOGLE_FORM;
        case "vscode", "windsurf", "cursor", "kiro" -> VS_CODE_GOOGLE_FORM;
        default -> null;
      };
      default -> null;
    };
  }
}
