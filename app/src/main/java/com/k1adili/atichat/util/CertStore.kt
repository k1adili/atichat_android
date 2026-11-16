package com.k1adili.atichat.util

import com.k1adili.atichat.core.Protocol
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.Date

/**
 * Stable self-signed EC (P-256) certificate used for TLS fingerprint pinning, like `_make_tls_files`
 * in the Windows app. Created once, kept in the app's private storage, and excluded from backups.
 */
object CertStore {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Returns (certificate DER, private key PKCS#8 DER). */
    fun loadOrCreate(dir: File): Pair<ByteArray, ByteArray> {
        dir.mkdirs()
        val certFile = File(dir, "peer-cert.der")
        val keyFile = File(dir, "peer-key.pk8")
        if (certFile.isFile && keyFile.isFile) return certFile.readBytes() to keyFile.readBytes()

        val keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val subject = X500Name("CN=${Protocol.TLS_PEER_NAME}")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(63, SecureRandom()).add(BigInteger.ONE),
            Date(now - DAY_MS),
            Date(now + 3650 * DAY_MS),
            subject,
            keyPair.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        val certDer = builder.build(signer).encoded
        val keyDer = keyPair.private.encoded

        // Write the key first; a half-written pair is regenerated on the next start.
        keyFile.writeBytes(keyDer)
        certFile.writeBytes(certDer)
        return certDer to keyDer
    }
}
