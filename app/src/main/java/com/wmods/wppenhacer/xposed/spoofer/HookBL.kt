package com.wmods.wppenhacer.xposed.spoofer

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import org.bouncycastle.asn1.ASN1Boolean
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Enumerated
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1TaggedObject
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.DERTaggedObject
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.io.pem.PemReader
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import java.util.LinkedList
import javax.xml.parsers.DocumentBuilderFactory

object HookBL {
    private var keyPair_EC: KeyPair
    private var keyPair_RSA: KeyPair
    private val certs_EC = LinkedList<Certificate>()
    private val certs_RSA = LinkedList<Certificate>()
    private var attestationChallengeBytes = ByteArray(1)

    init {
        try {
            var str = """
                    -----BEGIN EC PRIVATE KEY-----
                    MHcCAQEEICHghkMqFRmEWc82OlD8FMnarfk19SfC39ceTW28QuVEoAoGCCqGSM49
                    AwEHoUQDQgAE6555+EJjWazLKpFMiYbMcK2QZpOCqXMmE/6sy/ghJ0whdJdKKv6l
                    uU1/ZtTgZRBmNbxTt6CjpnFYPts+Ea4QFA==
                    -----END EC PRIVATE KEY-----""".trimIndent()

            keyPair_EC = parseKeyPair(str)

            str = """
                    -----BEGIN RSA PRIVATE KEY-----
                    MIICXQIBAAKBgQDAgyPcVogbuDAgafWwhWHG7r5/BeL1qEIEir6LR752/q7yXPKb
                    KvoyABQWAUKZiaFfz8aBXrNjWDwv0vIL5Jgyg92BSxbX4YVBeuVKvClqOm21wAQI
                    O2jFVsHwIzmRZBmGTVC3TUCuykhMdzVsiVoMJ1q/rEmdXX0jYvKcXgLocQIDAQAB
                    AoGBAL6GCwuZqAKm+xpZQ4p7txUGWwmjbcbpysxr88AsNNfXnpTGYGQo2Ix7f2V3
                    wc3qZAdKvo5yht8fCBHclygmCGjeldMu/Ja20IT/JxpfYN78xwPno45uKbqaPF/C
                    woB2tqiWrx0014gozpvdsfNPnJQEQweBKY4gExZyW728mTpBAkEA4cbZJ2RsCRbs
                    NoJtWUmDdAwh8bB0xKGlmGfGaXlchdPcRkxbkp6Uv7NODcxQFLEPEzQat/3V9gQU
                    0qMmytQcxQJBANpIWZd4XNVjD7D9jFJU+Y5TjhiYOq6ea35qWntdNDdVuSGOvUAy
                    DSg4fXifdvohi8wti2il9kGPu+ylF5qzr70CQFD+/DJklVlhbtZTThVFCTKdk6PY
                    ENvlvbmCKSz3i9i624Agro1X9LcdBThv/p6dsnHKNHejSZnbdvjl7OnA1J0CQBW3
                    TPJ8zv+Ls2vwTZ2DRrCaL3DS9EObDyasfgP36dH3fUuRX9KbKCPwOstdUgDghX/y
                    qAPpPu6W1iNc6VRCvCECQQCQp0XaiXCyzWSWYDJCKMX4KFb/1mW6moXI1g8bi+5x
                    fs0scurgHa2GunZU1M9FrbXx8rMdn4Eiz6XxpVcPmy0l
                    -----END RSA PRIVATE KEY-----""".trimIndent()

            keyPair_RSA = parseKeyPair(str)

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIICeDCCAh6gAwIBAgICEAEwCgYIKoZIzj0EAwIwgZgxCzAJBgNVBAYTAlVTMRMw
                    EQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MRUwEwYD
                    VQQKDAxHb29nbGUsIEluYy4xEDAOBgNVBAsMB0FuZHJvaWQxMzAxBgNVBAMMKkFu
                    ZHJvaWQgS2V5c3RvcmUgU29mdHdhcmUgQXR0ZXN0YXRpb24gUm9vdDAeFw0xNjAx
                    MTEwMDQ2MDlaFw0yNjAxMDgwMDQ2MDlaMIGIMQswCQYDVQQGEwJVUzETMBEGA1UE
                    CAwKQ2FsaWZvcm5pYTEVMBMGA1UECgwMR29vZ2xlLCBJbmMuMRAwDgYDVQQLDAdB
                    bmRyb2lkMTswOQYDVQQDDDJBbmRyb2lkIEtleXN0b3JlIFNvZnR3YXJlIEF0dGVz
                    dGF0aW9uIEludGVybWVkaWF0ZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOue
                    efhCY1msyyqRTImGzHCtkGaTgqlzJhP+rMv4ISdMIXSXSir+pblNf2bU4GUQZjW8
                    U7ego6ZxWD7bPhGuEBSjZjBkMB0GA1UdDgQWBBQ//KzWGrE6noEguNUlHMVlux6R
                    qTAfBgNVHSMEGDAWgBTIrel3TEXDo88NFhDkeUM6IVowzzASBgNVHRMBAf8ECDAG
                    AQH/AgEAMA4GA1UdDwEB/wQEAwIChDAKBggqhkjOPQQDAgNIADBFAiBLipt77oK8
                    wDOHri/AiZi03cONqycqRZ9pDMfDktQPjgIhAO7aAV229DLp1IQ7YkyUBO86fMy9
                    Xvsiu+f+uXc/WT/7
                    -----END CERTIFICATE-----""".trimIndent()

            certs_EC.add(parseCert(str))

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIICizCCAjKgAwIBAgIJAKIFntEOQ1tXMAoGCCqGSM49BAMCMIGYMQswCQYDVQQG
                    EwJVUzETMBEGA1UECAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwNTW91bnRhaW4gVmll
                    dzEVMBMGA1UECgwMR29vZ2xlLCBJbmMuMRAwDgYDVQQLDAdBbmRyb2lkMTMwMQYD
                    VQQDDCpBbmRyb2lkIEtleXN0b3JlIFNvZnR3YXJlIEF0dGVzdGF0aW9uIFJvb3Qw
                    HhcNMTYwMTExMDA0MzUwWhcNMzYwMTA2MDA0MzUwWjCBmDELMAkGA1UEBhMCVVMx
                    EzARBgNVBAgMCkNhbGlmb3JuaWExFjAUBgNVBAcMDU1vdW50YWluIFZpZXcxFTAT
                    BgNVBAoMDEdvb2dsZSwgSW5jLjEQMA4GA1UECwwHQW5kcm9pZDEzMDEGA1UEAwwq
                    QW5kcm9pZCBLZXlzdG9yZSBTb2Z0d2FyZSBBdHRlc3RhdGlvbiBSb290MFkwEwYH
                    KoZIzj0CAQYIKoZIzj0DAQcDQgAE7l1ex+HA220Dpn7mthvsTWpdamguD/9/SQ59
                    dx9EIm29sa/6FsvHrcV30lacqrewLVQBXT5DKyqO107sSHVBpKNjMGEwHQYDVR0O
                    BBYEFMit6XdMRcOjzw0WEOR5QzohWjDPMB8GA1UdIwQYMBaAFMit6XdMRcOjzw0W
                    EOR5QzohWjDPMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgKEMAoGCCqG
                    SM49BAMCA0cAMEQCIDUho++LNEYenNVg8x1YiSBq3KNlQfYNns6KGYxmSGB7AiBN
                    C/NR2TB8fVvaNTQdqEcbY6WFZTytTySn502vQX3xvw==
                    -----END CERTIFICATE-----""".trimIndent()

            certs_EC.add(parseCert(str))

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIICtjCCAh+gAwIBAgICEAAwDQYJKoZIhvcNAQELBQAwYzELMAkGA1UEBhMCVVMx
                    EzARBgNVBAgMCkNhbGlmb3JuaWExFjAUBgNVBAcMDU1vdW50YWluIFZpZXcxFTAT
                    BgNVBAoMDEdvb2dsZSwgSW5jLjEQMA4GA1UECwwHQW5kcm9pZDAeFw0xNjAxMDQx
                    MjQwNTNaFw0zNTEyMzAxMjQwNTNaMHYxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApD
                    YWxpZm9ybmlhMRUwEwYDVQQKDAxHb29nbGUsIEluYy4xEDAOBgNVBAsMB0FuZHJv
                    aWQxKTAnBgNVBAMMIEFuZHJvaWQgU29mdHdhcmUgQXR0ZXN0YXRpb24gS2V5MIGf
                    MA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDAgyPcVogbuDAgafWwhWHG7r5/BeL1
                    qEIEir6LR752/q7yXPKbKvoyABQWAUKZiaFfz8aBXrNjWDwv0vIL5Jgyg92BSxbX
                    4YVBeuVKvClqOm21wAQIO2jFVsHwIzmRZBmGTVC3TUCuykhMdzVsiVoMJ1q/rEmd
                    XX0jYvKcXgLocQIDAQABo2YwZDAdBgNVHQ4EFgQU1AwQG/jNY7n3OVK1DhNcpteZ
                    k4YwHwYDVR0jBBgwFoAUKfrxrMxN0kyWQCd1trDpMuUH/i4wEgYDVR0TAQH/BAgw
                    BgEB/wIBADAOBgNVHQ8BAf8EBAMCAoQwDQYJKoZIhvcNAQELBQADgYEAni1IX4xn
                    M9waha2Z11Aj6hTsQ7DhnerCI0YecrUZ3GAi5KVoMWwLVcTmnKItnzpPk2sxixZ4
                    Fg2Iy9mLzICdhPDCJ+NrOPH90ecXcjFZNX2W88V/q52PlmEmT7K+gbsNSQQiis6f
                    9/VCLiVE+iEHElqDtVWtGIL4QBSbnCBjBH8=
                    -----END CERTIFICATE-----""".trimIndent()

            certs_RSA.add(parseCert(str))

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIICpzCCAhCgAwIBAgIJAP+U2d2fB8gMMA0GCSqGSIb3DQEBCwUAMGMxCzAJBgNV
                    BAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBW
                    aWV3MRUwEwYDVQQKDAxHb29nbGUsIEluYy4xEDAOBgNVBAsMB0FuZHJvaWQwHhcN
                    MTYwMTA0MTIzMTA4WhcNMzUxMjMwMTIzMTA4WjBjMQswCQYDVQQGEwJVUzETMBEG
                    A1UECAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwNTW91bnRhaW4gVmlldzEVMBMGA1UE
                    CgwMR29vZ2xlLCBJbmMuMRAwDgYDVQQLDAdBbmRyb2lkMIGfMA0GCSqGSIb3DQEB
                    AQUAA4GNADCBiQKBgQCia63rbi5EYe/VDoLmt5TRdSMfd5tjkWP/96r/C3JHTsAs
                    Q+wzfNes7UA+jCigZtX3hwszl94OuE4TQKuvpSe/lWmgMdsGUmX4RFlXYfC78hdL
                    t0GAZMAoDo9Sd47b0ke2RekZyOmLw9vCkT/X11DEHTVm+Vfkl5YLCazOkjWFmwID
                    AQABo2MwYTAdBgNVHQ4EFgQUKfrxrMxN0kyWQCd1trDpMuUH/i4wHwYDVR0jBBgw
                    FoAUKfrxrMxN0kyWQCd1trDpMuUH/i4wDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8B
                    Af8EBAMCAoQwDQYJKoZIhvcNAQELBQADgYEAT3LzNlmNDsG5dFsxWfbwjSVJMJ6j
                    HBwp0kUtILlNX2S06IDHeHqcOd6os/W/L3BfRxBcxebrTQaZYdKumgf/93y4q+uc
                    DyQHXrF/unlx/U1bnt8Uqf7f7XzAiF343ZtkMlbVNZriE/mPzsF83O+kqrJVw4Op
                    Lvtc9mL1J1IXvmM=
                    -----END CERTIFICATE-----""".trimIndent()

            certs_RSA.add(parseCert(str))

        } catch (t: Throwable) {
            YLog.error("BLHook", t)
            throw RuntimeException(t)
        }
    }

    @Throws(Throwable::class)
    private fun parseKeyPair(key: String): KeyPair {
        val pemKeyPair = PEMParser(StringReader(key)).use { parser ->
            parser.readObject() as PEMKeyPair
        }
        return JcaPEMKeyConverter().getKeyPair(pemKeyPair)
    }

    @Throws(Throwable::class)
    private fun parseCert(cert: String): Certificate {
        val pemObject = PemReader(StringReader(cert)).use { reader ->
            reader.readPemObject()
        }
        val holder = X509CertificateHolder(pemObject.content)
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    private fun addHackedExtension(extension: Extension): Extension {
        try {
            val keyDescription = ASN1Sequence.getInstance(extension.extnValue.octets)
            val teeEnforcedEncodables = ASN1EncodableVector()
            val teeEnforcedAuthList =
                keyDescription.getObjectAt(7).toASN1Primitive() as ASN1Sequence

            for (asn1Encodable in teeEnforcedAuthList) {
                val taggedObject = asn1Encodable as ASN1TaggedObject
                if (taggedObject.tagNo == 704) continue
                teeEnforcedEncodables.add(taggedObject)
            }

            val random = SecureRandom()
            val bytes1 = ByteArray(32)
            val bytes2 = ByteArray(32)

            random.nextBytes(bytes1)
            random.nextBytes(bytes2)

            val rootOfTrustEncodables = arrayOf<ASN1Encodable>(
                DEROctetString(bytes1),
                ASN1Boolean.TRUE,
                ASN1Enumerated(0),
                DEROctetString(bytes2)
            )

            val rootOfTrustSeq: ASN1Sequence = DERSequence(rootOfTrustEncodables)
            val rootOfTrust: ASN1TaggedObject = DERTaggedObject(true, 704, rootOfTrustSeq)

            teeEnforcedEncodables.add(rootOfTrust)

            val attestationVersion = keyDescription.getObjectAt(0)
            val attestationSecurityLevel = keyDescription.getObjectAt(1)
            val keymasterVersion = keyDescription.getObjectAt(2)
            val keymasterSecurityLevel = keyDescription.getObjectAt(3)
            val attestationChallenge = keyDescription.getObjectAt(4)
            val uniqueId = keyDescription.getObjectAt(5)
            val softwareEnforced = keyDescription.getObjectAt(6)
            val teeEnforced = DERSequence(teeEnforcedEncodables)

            val keyDescriptionEncodables = arrayOf<ASN1Encodable>(
                attestationVersion,
                attestationSecurityLevel,
                keymasterVersion,
                keymasterSecurityLevel,
                attestationChallenge,
                uniqueId,
                softwareEnforced,
                teeEnforced
            )

            val keyDescriptionHackSeq: ASN1Sequence = DERSequence(keyDescriptionEncodables)
            val keyDescriptionOctetStr: ASN1OctetString = DEROctetString(keyDescriptionHackSeq)

            return Extension(
                ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17"),
                false,
                keyDescriptionOctetStr
            )
        } catch (t: Throwable) {
            YLog.error("BLHook", t)
        }
        return extension
    }

    private fun createHackedExtensions(): Extension? {
        try {
            val random = SecureRandom()
            val bytes1 = ByteArray(32)
            val bytes2 = ByteArray(32)

            random.nextBytes(bytes1)
            random.nextBytes(bytes2)

            val rootOfTrustEncodables = arrayOf<ASN1Encodable>(
                DEROctetString(bytes1),
                ASN1Boolean.TRUE,
                ASN1Enumerated(0),
                DEROctetString(bytes2)
            )

            val rootOfTrustSeq: ASN1Sequence = DERSequence(rootOfTrustEncodables)

            val purposesArray = arrayOf<ASN1Encodable>(
                ASN1Integer(0), ASN1Integer(1), ASN1Integer(2),
                ASN1Integer(3), ASN1Integer(4), ASN1Integer(5)
            )

            val digests = arrayOf<ASN1Encodable>(
                ASN1Integer(1), ASN1Integer(2), ASN1Integer(3),
                ASN1Integer(4), ASN1Integer(5), ASN1Integer(6)
            )

            val Apurpose = DERSet(purposesArray)
            val Aalgorithm = ASN1Integer(3)
            val AkeySize = ASN1Integer(256)
            val Adigest = DERSet(digests)
            val AecCurve = ASN1Integer(1)
            val AnoAuthRequired = DERNull.INSTANCE
            val AosVersion = ASN1Integer(130000)
            val AosPatchLevel = ASN1Integer(202401)
            val AcreationDateTime = ASN1Integer(System.currentTimeMillis())
            val Aorigin = ASN1Integer(0)

            val purpose = DERTaggedObject(true, 1, Apurpose)
            val algorithm = DERTaggedObject(true, 2, Aalgorithm)
            val keySize = DERTaggedObject(true, 3, AkeySize)
            val digest = DERTaggedObject(true, 5, Adigest)
            val ecCurve = DERTaggedObject(true, 10, AecCurve)
            val noAuthRequired = DERTaggedObject(true, 503, AnoAuthRequired)
            val creationDateTime = DERTaggedObject(true, 701, AcreationDateTime)
            val origin = DERTaggedObject(true, 702, Aorigin)
            val rootOfTrust = DERTaggedObject(true, 704, rootOfTrustSeq)
            val osVersion = DERTaggedObject(true, 705, AosVersion)
            val osPatchLevel = DERTaggedObject(true, 706, AosPatchLevel)

            val teeEnforcedEncodables = arrayOf<ASN1Encodable>(
                purpose, algorithm, keySize, digest, ecCurve, noAuthRequired,
                creationDateTime, origin, rootOfTrust, osVersion, osPatchLevel
            )

            val attestationVersion = ASN1Integer(4)
            val attestationSecurityLevel = ASN1Enumerated(1)
            val keymasterVersion = ASN1Integer(41)
            val keymasterSecurityLevel = ASN1Enumerated(1)
            val attestationChallenge = DEROctetString(attestationChallengeBytes)
            val uniqueId = DEROctetString("".toByteArray())
            val softwareEnforced: ASN1Sequence = DERSequence()
            val teeEnforced: ASN1Sequence = DERSequence(teeEnforcedEncodables)

            val keyDescriptionEncodables = arrayOf<ASN1Encodable>(
                attestationVersion,
                attestationSecurityLevel,
                keymasterVersion,
                keymasterSecurityLevel,
                attestationChallenge,
                uniqueId,
                softwareEnforced,
                teeEnforced
            )

            val keyDescriptionHackSeq: ASN1Sequence = DERSequence(keyDescriptionEncodables)
            val keyDescriptionOctetStr: ASN1OctetString = DEROctetString(keyDescriptionHackSeq)

            return Extension(
                ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17"),
                false,
                keyDescriptionOctetStr
            )
        } catch (t: Throwable) {
            YLog.error("BLHook", t)
        }
        return null
    }

    private fun createLeafCert(): Certificate? {
        try {
            val now = System.currentTimeMillis()
            val notBefore = Date(now)

            val calendar = Calendar.getInstance()
            calendar.time = notBefore
            calendar.add(Calendar.HOUR, 1)

            val notAfter = calendar.time

            val certBuilder = JcaX509v3CertificateBuilder(
                X500Name("CN=chiteroman"),
                BigInteger.ONE,
                notBefore,
                notAfter,
                X500Name("CN=Android Keystore Key"),
                keyPair_EC.public
            )

            val keyUsage = KeyUsage(KeyUsage.keyCertSign)
            certBuilder.addExtension(Extension.keyUsage, true, keyUsage)

            val hackedExtensions = createHackedExtensions()
            if (hackedExtensions != null) {
                certBuilder.addExtension(hackedExtensions)
            }

            val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair_EC.private)
            val certHolder = certBuilder.build(contentSigner)

            return JcaX509CertificateConverter().getCertificate(certHolder)
        } catch (t: Throwable) {
            YLog.error("BLHook", t)
        }
        return null
    }

    private fun hackLeafExistingCert(certificate: Certificate): Certificate {
        try {
            val certificateHolder = X509CertificateHolder(certificate.encoded)

            val keyPair: KeyPair =
                if (KeyProperties.KEY_ALGORITHM_EC == certificate.publicKey.algorithm) {
                    keyPair_EC
                } else {
                    keyPair_RSA
                }

            val now = System.currentTimeMillis()
            val notBefore = Date(now)

            val calendar = Calendar.getInstance()
            calendar.time = notBefore
            calendar.add(Calendar.HOUR, 1)

            val notAfter = calendar.time

            val certBuilder = JcaX509v3CertificateBuilder(
                certificateHolder.issuer,
                certificateHolder.serialNumber,
                notBefore,
                notAfter,
                certificateHolder.subject,
                keyPair.public
            )

            for (extensionOID in certificateHolder.extensionOIDs) {
                val identifier = extensionOID as ASN1ObjectIdentifier
                if ("1.3.6.1.4.1.11129.2.1.17" == identifier.id) continue
                certBuilder.addExtension(certificateHolder.getExtension(identifier))
            }

            val extension =
                certificateHolder.getExtension(ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17"))
            certBuilder.addExtension(addHackedExtension(extension))

            val contentSigner =
                if (KeyProperties.KEY_ALGORITHM_EC == certificate.publicKey.algorithm) {
                    JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
                } else {
                    JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
                }

            val certHolder = certBuilder.build(contentSigner)
            return JcaX509CertificateConverter().getCertificate(certHolder)
        } catch (t: Throwable) {
            YLog.error("BLHook", t)
        }
        return certificate
    }

    @Throws(Throwable::class)
    private fun parseBootloaderSpooferXml(xmlContent: String) {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(InputSource(StringReader(xmlContent)))

        val ecKeys = doc.getElementsByTagName("Key")
        for (i in 0 until ecKeys.length) {
            val keyElement = ecKeys.item(i) as Element
            val algorithm = keyElement.getAttribute("algorithm")

            val privateKeyNodes = keyElement.getElementsByTagName("PrivateKey")
            if (privateKeyNodes.length > 0) {
                val privateKeyContent =
                    privateKeyNodes.item(0).textContent.replace(Regex(" {2,}"), "")

                val certChainNodes = keyElement.getElementsByTagName("CertificateChain")
                if (certChainNodes.length > 0) {
                    val certChainElement = certChainNodes.item(0) as Element
                    val certificateNodes = certChainElement.getElementsByTagName("Certificate")

                    if ("ecdsa" == algorithm || "ec" == algorithm) {
                        keyPair_EC = parseKeyPair(privateKeyContent)
                        certs_EC.clear()

                        for (j in 0 until certificateNodes.length) {
                            val certContent =
                                certificateNodes.item(j).textContent.replace(Regex(" {2,}"), "")
                            certs_EC.add(parseCert(certContent))
                        }
                    } else if ("rsa" == algorithm) {
                        keyPair_RSA = parseKeyPair(privateKeyContent)
                        certs_RSA.clear()

                        for (j in 0 until certificateNodes.length) {
                            val certContent =
                                certificateNodes.item(j).textContent.replace(Regex(" {2,}"), "")
                            certs_RSA.add(parseCert(certContent))
                        }
                    }
                }
            }
        }
    }

    fun hook(packageParam: PackageParam, loader: ClassLoader, prefs: SharedPreferences) {
        val useCustomSpoofer = prefs.getBoolean("bootloader_spoofer_custom", false)
        if (useCustomSpoofer) {
            val xmlContent = prefs.getString("bootloader_spoofer_xml", "") ?: ""
            if (xmlContent.isNotEmpty()) {
                try {
                    parseBootloaderSpooferXml(xmlContent)
                    YLog.debug("Successfully loaded custom bootloader spoofer keys")
                } catch (t: Throwable) {
                    YLog.error("HookBL", t)
                    Utils.showToast(
                        "Error parsing custom bootloader spoofer XML: ${t.message}",
                        Toast.LENGTH_LONG
                    )
                }
            }
        }

        try {
            val app = FeatureLoader.mApp
            val packageManagerClass: Class<*>
            val sharedPreferencesClass: Class<*>

            if (app == null) {
                packageManagerClass =
                    ReflectionUtils.findClass("android.app.ApplicationPackageManager", loader)
                sharedPreferencesClass =
                    ReflectionUtils.findClass("android.app.SharedPreferencesImpl", loader)
            } else {
                packageManagerClass = app.packageManager.javaClass
                sharedPreferencesClass =
                    app.getSharedPreferences("settings", Context.MODE_PRIVATE).javaClass
            }

            packageParam.apply {
                val hookSystemFeature: HookParam.() -> Unit = {
                    val featureName = args[0] as String

                    if (PackageManager.FEATURE_STRONGBOX_KEYSTORE == featureName) {
                        result = false
                    } else if (PackageManager.FEATURE_KEYSTORE_APP_ATTEST_KEY == featureName) {
                        result = false
                    } else if ("android.software.device_id_attestation" == featureName) {
                        result = false
                    }
                }
                packageManagerClass.resolve().apply {
                    firstMethod {
                        name = "hasSystemFeature"
                        parameters(String::class)
                    }.hook { before(hookSystemFeature) }
                    firstMethod {
                        name = "hasSystemFeature"
                        parameters(String::class, Int::class)
                    }.hook { before(hookSystemFeature) }
                }

                sharedPreferencesClass.resolve().firstMethod {
                    name = "getBoolean"
                }.hook {
                    before {
                        val key = args[0] as String
                        if ("prefer_attest_key" == key)
                            result = false
                    }
                }
            }

        } catch (t: Throwable) {
            YLog.error("BLHook", t)
        }

        try {
            packageParam.apply {
                KeyGenParameterSpec.Builder::class.resolve().firstMethod {
                    name = "setAttestationChallenge"
                    parameters(ByteArray::class)
                }.hook().before { attestationChallengeBytes = args[0] as ByteArray }
            }
        } catch (t: Throwable) {
            YLog.error("BLHook", t)
        }

        try {
            packageParam.apply {

                val keyPairGeneratorSpi_EC =
                    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                keyPairGeneratorSpi_EC.javaClass.resolve().firstMethod {
                    name = "generateKeyPair"
                }.hook().replaceTo(keyPair_EC)

                val keyPairGeneratorSpi_RSA =
                    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")

                keyPairGeneratorSpi_RSA.javaClass.resolve().firstMethod {
                    name = "generateKeyPair"
                }.hook().replaceTo(keyPair_RSA)

            }
        } catch (t: Throwable) {
            YLog.error("BLHook", t)
        }

        try {
            packageParam.apply {

                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                val keyStoreSpi = ReflectionUtils.getObjectField(keyStore, "keyStoreSpi")

                keyStoreSpi!!.javaClass.resolve().firstMethod {
                    name = "engineGetCertificateChain"
                    parameters(String::class)
                }.hook().after {
                    var certificates: Array<Certificate>? = null

                    try {
                        @Suppress("UNCHECKED_CAST")
                        certificates = result as? Array<Certificate>
                    } catch (t: Throwable) {
                        YLog.error("BLHook", t)
                    }

                    val certificateList = LinkedList<Certificate>()

                    if (certificates == null) {
                        certificateList.addAll(certs_EC)
                        val leafCert = createLeafCert()
                        if (leafCert != null) {
                            certificateList.addFirst(leafCert)
                        }
                    } else {
                        val x509Certificate =
                            certificates.getOrNull(0) as? X509Certificate ?: return@after
                        val bytes =
                            x509Certificate.getExtensionValue("1.3.6.1.4.1.11129.2.1.17")

                        if (bytes == null || bytes.isEmpty()) return@after

                        val algorithm = x509Certificate.publicKey.algorithm
                        if (KeyProperties.KEY_ALGORITHM_EC == algorithm) {
                            certificateList.addAll(certs_EC)
                        } else if (KeyProperties.KEY_ALGORITHM_RSA == algorithm) {
                            certificateList.addAll(certs_RSA)
                        }
                        certificateList.addFirst(hackLeafExistingCert(x509Certificate))
                    }
                    result = certificateList.toTypedArray()
                }

            }
        } catch (t: Throwable) {
            YLog.error("BLHook", t)
        }
    }
}