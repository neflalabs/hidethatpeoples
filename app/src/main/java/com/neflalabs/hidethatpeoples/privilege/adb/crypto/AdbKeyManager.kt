package com.neflalabs.hidethatpeoples.privilege.adb.crypto

import android.content.Context
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

class AdbKeyManager(private val context: Context) {

    companion object {
        private const val TAG = "AdbKeyManager"
        private const val KEY_DIR = "adb_keys"
        private const val PRIVATE_KEY_FILE = "adb_private.key"
        private const val CERTIFICATE_FILE = "adb_cert.crt"
        private const val KEY_SIZE = 2048
    }

    private val keyDir: File by lazy {
        File(context.noBackupFilesDir, KEY_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private val privateKeyFile: File by lazy { File(keyDir, PRIVATE_KEY_FILE) }
    private val certificateFile: File by lazy { File(keyDir, CERTIFICATE_FILE) }

    private var cachedPrivateKey: PrivateKey? = null
    private var cachedCertificate: Certificate? = null

    @Synchronized
    fun getOrCreateKeyPair(): Pair<PrivateKey, Certificate> {
        cachedPrivateKey?.let { priv ->
            cachedCertificate?.let { cert ->
                return Pair(priv, cert)
            }
        }

        if (privateKeyFile.exists() && certificateFile.exists()) {
            try {
                val privKey = loadPrivateKey(privateKeyFile)
                val cert = loadCertificate(certificateFile)
                cachedPrivateKey = privKey
                cachedCertificate = cert
                Log.d(TAG, "Loaded existing ADB keypair and certificate successfully")
                return Pair(privKey, cert)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to load existing keys, regenerating...", e)
            }
        }

        val generated = generateKeyPairAndCert()
        saveKeys(generated.first, generated.second)
        cachedPrivateKey = generated.first
        cachedCertificate = generated.second
        return generated
    }

    fun getPrivateKey(): PrivateKey = getOrCreateKeyPair().first

    fun getCertificate(): Certificate = getOrCreateKeyPair().second

    private fun generateKeyPairAndCert(): Pair<PrivateKey, X509Certificate> {
        Log.i(TAG, "Generating new RSA $KEY_SIZE bit keypair for Wireless ADB...")
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(KEY_SIZE, SecureRandom())
        val keyPair = kpg.generateKeyPair()

        val now = System.currentTimeMillis()
        val startDate = Date(now - 24 * 60 * 60 * 1000L) // Yesterday to guard against clock skew
        val expiryDate = Date(now + 25L * 365 * 24 * 60 * 60 * 1000L) // 25 years

        val serial = BigInteger(64, SecureRandom())
        val subject = X500Name("CN=HideThatPeoples, O=Android, C=US")

        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            startDate,
            expiryDate,
            subject,
            keyPair.public
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        val cert = JcaX509CertificateConverter().getCertificate(certHolder)

        return Pair(keyPair.private, cert)
    }

    private fun saveKeys(privateKey: PrivateKey, certificate: Certificate) {
        try {
            privateKeyFile.writeBytes(privateKey.encoded)
            certificateFile.writeBytes(certificate.encoded)
            Log.d(TAG, "Saved ADB credentials to ${keyDir.absolutePath}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to persist ADB keys", e)
        }
    }

    private fun loadPrivateKey(file: File): PrivateKey {
        val bytes = file.readBytes()
        val spec = PKCS8EncodedKeySpec(bytes)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePrivate(spec)
    }

    private fun loadCertificate(file: File): Certificate {
        val bytes = file.readBytes()
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(bytes.inputStream())
    }
}
