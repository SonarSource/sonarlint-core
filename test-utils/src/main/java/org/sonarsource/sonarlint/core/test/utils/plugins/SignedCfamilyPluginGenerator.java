/*
 * SonarLint Core - Test Utils
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
package org.sonarsource.sonarlint.core.test.utils.plugins;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyPacket;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifact;

public class SignedCfamilyPluginGenerator {

  private static final String PUBLIC_KEY_RESOURCE_PATH = "ondemand/sonarsource-public.key";
  private static final String JAR_RESOURCE_PATH = "ondemand/fake-cpp-plugin.jar";
  private static final int KEY_SIZE = 2048;

  private SignedCfamilyPluginGenerator() {
    // utility class
  }

  public static void main(String[] args) {
    try {
      generate(Path.of(args[0]));
    } catch (IOException | GeneralSecurityException | PGPException e) {
      throw new IllegalStateException("Error when generating the signed fake plugin", e);
    }
  }

  public static void generate(Path outputDirectory) throws IOException, GeneralSecurityException, PGPException {
    var jarPath = outputDirectory.resolve(JAR_RESOURCE_PATH);
    Files.createDirectories(jarPath.getParent());
    var generatedJar = SonarPluginBuilder.newSonarPlugin(BinariesArtifact.CFAMILY_PLUGIN.artifactKey()).generate(jarPath.getParent());
    Files.move(generatedJar, jarPath, StandardCopyOption.REPLACE_EXISTING);
    sign(jarPath, outputDirectory);
  }

  private static void sign(Path jarPath, Path outputDirectory) throws IOException, GeneralSecurityException, PGPException {
    var provider = new BouncyCastleProvider();
    var keyPairGenerator = KeyPairGenerator.getInstance("RSA", provider);
    keyPairGenerator.initialize(KEY_SIZE);
    var keyPair = new JcaPGPKeyPair(PublicKeyPacket.VERSION_4, PublicKeyAlgorithmTags.RSA_GENERAL, keyPairGenerator.generateKeyPair(), Date.from(Instant.now()));
    var signerBuilder = new JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_GENERAL, HashAlgorithmTags.SHA256).setProvider(provider);
    var keyRingGenerator = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, keyPair, "SonarLint test signing key",
      new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1), null, null, signerBuilder, null);

    var signatureGenerator = new PGPSignatureGenerator(signerBuilder, keyPair.getPublicKey());
    signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, keyPair.getPrivateKey());
    signatureGenerator.update(Files.readAllBytes(jarPath));

    writeArmored(outputDirectory.resolve(PUBLIC_KEY_RESOURCE_PATH), keyRingGenerator.generatePublicKeyRing()::encode);
    writeArmored(outputDirectory.resolve(BinariesArtifact.CFAMILY_PLUGIN.signatureResourcePath()), signatureGenerator.generate()::encode);
  }

  private static void writeArmored(Path target, Encoder encoder) throws IOException {
    Files.createDirectories(target.getParent());
    try (var out = new ArmoredOutputStream(Files.newOutputStream(target))) {
      encoder.encode(out);
    }
  }

  @FunctionalInterface
  private interface Encoder {
    void encode(OutputStream out) throws IOException;
  }
}
