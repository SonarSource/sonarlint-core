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
package org.sonarsource.sonarlint.core.plugin.ondemand;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Verifies the PGP signature of downloaded plugins using the SonarSource public key.
 */
class OnDemandPluginSignatureVerifier {

  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String SONAR_PUBLIC_KEY = "ondemand/sonarsource-public.key";
  private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

  boolean verify(Path jarFile, String pluginKey) {
    var keyRing = loadPublicKeyRing();
    if (keyRing == null) {
      return false;
    }
    var isValid = verifyPgpSignature(jarFile, pluginKey, keyRing);
    if (isValid) {
      LOG.debug("Plugin file signature verified successfully");
    }
    return isValid;
  }

  private PGPPublicKeyRingCollection loadPublicKeyRing() {
    try (var keyStream = getClass().getClassLoader().getResourceAsStream(SONAR_PUBLIC_KEY)) {
      if (keyStream == null) {
        throw new FileNotFoundException("PGP key not found in resources: " + SONAR_PUBLIC_KEY);
      }

      var decoder = PGPUtil.getDecoderStream(new BufferedInputStream(keyStream));
      return new PGPPublicKeyRingCollection(decoder, new JcaKeyFingerprintCalculator());
    } catch (IOException | PGPException e) {
      LOG.error("Error loading public key ring", e);
      return null;
    }
  }

  private InputStream loadBundledSignature(String pluginKey) {
    var signatureFileName = String.format("ondemand/sonar-%s-plugin.jar.asc", pluginKey);
    return getClass().getClassLoader().getResourceAsStream(signatureFileName);
  }

  private boolean verifyPgpSignature(Path dataFile, String pluginKey, PGPPublicKeyRingCollection keyRing) {
    try (var signatureStream = loadBundledSignature(pluginKey)) {
      if (signatureStream == null) {
        LOG.error("Could not find bundled signature for plugin: {}", pluginKey);
        return false;
      }

      try (var decoderStream = PGPUtil.getDecoderStream(new BufferedInputStream(signatureStream))) {
        var pgpFact = new PGPObjectFactory(decoderStream, new JcaKeyFingerprintCalculator());

        // Handle both compressed and uncompressed signature formats
        var signatureList = extractSignatureList(pgpFact);
        if (signatureList == null || signatureList.isEmpty()) {
          LOG.error("No signatures found in signature file");
          return false;
        }

        var signature = signatureList.get(0);
        var publicKey = keyRing.getPublicKey(signature.getKeyID());
        if (publicKey == null) {
          LOG.error("Public key not found for signature keyID={}", signature.getKeyID());
          return false;
        }

        signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider(BOUNCY_CASTLE_PROVIDER), publicKey);

        try (var dataIn = new FileInputStream(dataFile.toFile())) {
          var buffer = new byte[8192];
          int bytesRead;
          while ((bytesRead = dataIn.read(buffer)) != -1) {
            signature.update(buffer, 0, bytesRead);
          }
        }

        return signature.verify();
      }
    } catch (IOException | PGPException e) {
      LOG.error("Error verifying PGP signature", e);
      return false;
    }
  }

  private static PGPSignatureList extractSignatureList(PGPObjectFactory pgpFact) {
    try {
      var obj = pgpFact.nextObject();
      if (obj instanceof PGPCompressedData compressedData) {
        var innerFactory = new PGPObjectFactory(compressedData.getDataStream(), new JcaKeyFingerprintCalculator());
        var innerObj = innerFactory.nextObject();
        if (innerObj instanceof PGPSignatureList signatureList) {
          return signatureList;
        }
      } else if (obj instanceof PGPSignatureList signatureList) {
        return signatureList;
      }
    } catch (IOException | PGPException e) {
      LOG.error("Error extracting signature list", e);
    }
    return null;
  }
}
