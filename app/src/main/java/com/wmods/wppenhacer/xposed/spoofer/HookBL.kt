package com.wmods.wppenhacer.xposed.spoofer

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
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
    private var keyPair_EC: KeyPair? = null
    private var keyPair_RSA: KeyPair? = null
    private val certs_EC = LinkedList<Certificate>()
    private val certs_RSA = LinkedList<Certificate>()
    private var attestationChallengeBytes = ByteArray(1)

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
        val keyPair = keyPair_EC ?: return null
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
                keyPair.public
            )

            val keyUsage = KeyUsage(KeyUsage.keyCertSign)
            certBuilder.addExtension(Extension.keyUsage, true, keyUsage)

            val hackedExtensions = createHackedExtensions()
            if (hackedExtensions != null) {
                certBuilder.addExtension(hackedExtensions)
            }

            val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
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

            val keyPair =
                if (KeyProperties.KEY_ALGORITHM_EC == certificate.publicKey.algorithm) {
                    keyPair_EC
                } else {
                    keyPair_RSA
                } ?: return certificate

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
        val xmlContent = prefs.getString("bootloader_spoofer_xml", "") ?: ""
        if (xmlContent.isEmpty()) return
        try {
            parseBootloaderSpooferXml(xmlContent)
        } catch (t: Throwable) {
            keyPair_EC = null
            keyPair_RSA = null
            certs_EC.clear()
            certs_RSA.clear()
            YLog.error("HookBL", t)
            Utils.showToast(
                "Error parsing custom keybox XML: ${t.message}",
                Toast.LENGTH_LONG
            )
            return
        }
        if (keyPair_EC == null && keyPair_RSA == null) return
        YLog.debug("Successfully loaded custom keybox keys")

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

                val keyPairGenerator =
                    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                keyPairGenerator.javaClass.resolve().firstMethod {
                    name = "generateKeyPair"
                }.hook().before {
                    val generator = instanceOrNull<KeyPairGenerator>() ?: return@before
                    if (generator.provider?.name != "AndroidKeyStore") return@before
                    val keyPair = when (generator.algorithm) {
                        KeyProperties.KEY_ALGORITHM_EC -> keyPair_EC
                        KeyProperties.KEY_ALGORITHM_RSA -> keyPair_RSA
                        else -> null
                    }
                    if (keyPair != null) result = keyPair
                }

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