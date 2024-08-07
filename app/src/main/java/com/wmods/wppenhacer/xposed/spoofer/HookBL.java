package com.wmods.wppenhacer.xposed.spoofer;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.StringReader;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyPairGeneratorSpi;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookBL {
    private static final KeyPair keyPair_EC, keyPair_RSA;
    private static final LinkedList<Certificate> certs_EC = new LinkedList<>();
    private static final LinkedList<Certificate> certs_RSA = new LinkedList<>();
    private static byte[] attestationChallengeBytes = new byte[1];

    static {
        try {

            String str = """
                    -----BEGIN EC PRIVATE KEY-----
                    MHcCAQEEIA2Pc+RjAdMyqaCpctIuSyCGhsPnTHPeOxhM7QmGb1gNoAoGCCqGSM49
                    AwEHoUQDQgAEWu/pudQWIK+TPC7f9dftyxq5te8xMChNHYALeeiB6zk8laiG9zu9
                    cae9EY763/thIoZQXQTVQK43+QHcKnOetQ==
                    -----END EC PRIVATE KEY-----""";

            keyPair_EC = parseKeyPair(str);

            str = """
                    -----BEGIN RSA PRIVATE KEY-----
                    MIIG4gIBAAKCAYEA46CnsChjrTJK+xBGJmPn06WJ6ofOxPGdLVgnX5IZcQMY19hx
                    NFMWtLAidMWY63h2U6KEL0eTYTA1Y51H/oKEPCPf/beyEzN54zz/cozLlPgngQuV
                    TNEaHFa8uV3f5xnQvFDMxW9LELNS9R75zlULjitPL0uND/TALuBnnQW4dPxorGP9
                    b06UAh3ONgAcHlsSh755oLZe/R6uEI/cw6DEUB6PoQDLPT/kFHreqpJ7ZjzU3wVe
                    LEYMi/g5c2vk3Zn3VHv10ge1MfbmOKgA1n01Vu0wXJS038EizXBUdfuKGyIXBXL+
                    /4/K87p0oL3PkwdAfmWDOM55XaMRMeIZ/6FWsVvTZqyzPbzcN1EImhMvOsTAnDzW
                    QkoyFcI8P83OF24JvA1VQbvIqdKWPTBg9sFfRFj9m3xr/q7EBjg+VHk9KXPv7/C4
                    szfJSwxp1rb859bgmHP3BbDDYRlhX+r+HV0j5ZkLSbx1J10qHuIcg9lavySwATai
                    11x3lccjtRTuqmsnAgMBAAECggGAJzPpDW63VAHgZgL0gfIc4BeXfBvtoX+XOVMA
                    XPZViWAmLC5ruL5Cn9Xh6UiKwKsbfeX0bAwgttFNsJ1K4+toiM8I6bJ0adgoutkr
                    iXf0K/DdJN1w1i0BGTZbBHXFdRC+IWCtE6fKPtMTi4HNNhrxhYex+IxDfCcD3k7p
                    qWJt1UAPavlBkaTv2rl1ZVl5fkTjGRvV6Lbf/yoEgne0ZsDObpoE1I7FcgQJ7PkI
                    VkoqFH6PjuVmxas+x/B1PIqtfPjiC5O7WpKujXVaj+Ft7kpf0GP5gRR92eTWouW4
                    gAO1bHzFzkHIFQafqgYThU5xIZzwNzzRZ9uNj3Yw2xcaBa9QFKFtOuAPDzwEU62N
                    FOKl6yi0B2fwpWcW6tDuRskThe2QV7TBeQmzueXFZ3EQQ8bIqkXUNp/cVVEFKOf0
                    f5Hq77g1QtgejAQXzb0kKm/f+ibTy6gZofAnXJ2ANtwSXtsTd9Uu/cyY53L7X7Wu
                    kg3wI68uEGkwAoMuxMi/PdlmkwMpAoHBAP9is4nU388qzzpeiZnpv/BQbNtQWY/o
                    4cskGOrBPtySmQlzdvWiOI9TShM8uHYmdZryqSU9OqPCBcvDrOIabkrcKDVdy5RV
                    oYunOIfXDi3UQC6gzBhm4k0POo5HN8DmQGWMlqVCoEGOQwjLVLY/WNFJJHLtNG2z
                    xHNPp5bdEU91C8/vCoXHLY0R6IUtW53hvVcrXr5yZyxDjpbaSWXt4G8CGKWG1/i1
                    oTfPg6lpIeu33a2CnRdmbDh4nTMxoNWqjQKBwQDkLNtZUmcQA4tXYMNtppOGx1xl
                    ZGgMwvHKVfQmc5N6kePAz2raTf7iG1TYFIGwe/T1KWV1Ur/fA3HAb3Z2+Ryc/VMM
                    LEXH8+gM/XcyhBmpMnnwXbBFbbnu2iERUnWb+kBLHWXdhK9kCXYJrQVtu46IsDbd
                    qkyQnAuHRUbYroI6lzG3JNO2H55mS5GuWOEW/3VwmTJH+Wc8DQhfUfQt9IeNtZ3Q
                    ppg2zKrivUPUDbEFVrDQVFJrFafAHbFWk0x1+YMCgcA6qaNwD7PxVHYRhFG7RxRN
                    0UuP/R71jZsAHKSUmBsmc44vu5QhzEeNtKYZUSNWEHOHSiJMaYokv5axPZtivIuF
                    KpkZ2RfAVQMsnxa/LUkWgeDUVR9Mo6Vr6gmbUQSfjMRSDz8zauCA7nq7dGlbC6YN
                    PO3gcFhQsrB3hF6Mqu8k/0/dTZ1vKVdvRiPRI6Ad3hKuCUpUGNdWNZZ1VGLzxPZ+
                    yU58B0No39/OgB5QNdQS30NObehDzcUiG6KQdd/p9KkCgcEAzyZSNXN0EbvGAxvc
                    EG5MPO49Weef9wX80X7bWxVObNaJ+H/WOD7bCZdanZlwbDL9Hp3oeG0ZuHO+D8Ch
                    wj1C1tu9S63d5DvxDeoHDxuS2GL1BV1pLH8DcG8j2kAMegDl4cvcsRFG0gEs4nfs
                    F8VRD5M7HF8Bh7/DT5l7SjiUDnE5N1X9xrhJ7ADrT2dtYp2llwXd+i3YpVIHqdIT
                    Zq07a1HY5pZ4VjfVZGgDbj7H2VrsxxXmhUVxuB684yZzIh/VAoG/OabMPA2YFqtf
                    7rXDWwqT1pwMYOEupL9tO5kAky3Zz9zE/WKdfmIvGjZQxz64QhmtwGKm0VFkYAzT
                    LP1gZ+C4b6cRHLXNcZ7Z344Skz66B/Z9Z0/9VW8aJ/4M5zqcjfaGjbFjELd1KuDh
                    wjRT3LLWcjLZ1w/IJy6r+rqq0d7A26o0B5UfykqudVvjJ+eOq3ozhlzgdzrrhewb
                    jTU2mUlIrlv0ESD2M22HHTP8TD8DVvw8UkGsOsQ7bzWSRNALiqE=
                    -----END RSA PRIVATE KEY-----""";

            keyPair_RSA = parseKeyPair(str);

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIICJTCCAaugAwIBAgIKEJl1VCCDF2mHUzAKBggqhkjOPQQDAjApMRkwFwYDVQQF
                    ExA4YWJlMWM3NWRjMWNkNTk5MQwwCgYDVQQMDANURUUwHhcNMTgwNDE4MjEzMzQ5
                    WhcNMjgwNDE1MjEzMzQ5WjApMRkwFwYDVQQFExA2MGYyMjlmMTlmY2EzYTA0MQww
                    CgYDVQQMDANURUUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARa7+m51BYgr5M8
                    Lt/11+3LGrm17zEwKE0dgAt56IHrOTyVqIb3O71xp70Rjvrf+2EihlBdBNVArjf5
                    Adwqc561o4G6MIG3MB0GA1UdDgQWBBSriAVmksHYWuCRLlDvR47MzfcUfDAfBgNV
                    HSMEGDAWgBSQv/MlEz9XMxBFdPp9W+RNB1737TAPBgNVHRMBAf8EBTADAQH/MA4G
                    A1UdDwEB/wQEAwICBDBUBgNVHR8ETTBLMEmgR6BFhkNodHRwczovL2FuZHJvaWQu
                    Z29vZ2xlYXBpcy5jb20vYXR0ZXN0YXRpb24vY3JsLzEwOTk3NTU0MjA4MzE3Njk4
                    NzUzMAoGCCqGSM49BAMCA2gAMGUCMQDx5cRqI58iDdXLDkD0rvh1fjOSmejV5NCH
                    +KX5A2mZLzxxy8QkW3szHck0qvarVK0CMGDi3tjaKLiM3rPHyAJmltRDIvLZejwz
                    R+0oP0cs/vld5xBZSoXbvjCPe39IQryn7Q==
                    -----END CERTIFICATE-----""";

            certs_EC.add(parseCert(str));

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIID0TCCAbmgAwIBAgIKA4gmZ2BliZaFkTANBgkqhkiG9w0BAQsFADAbMRkwFwYD
                    VQQFExBmOTIwMDllODUzYjZiMDQ1MB4XDTE4MDQxODIxMTQ1NloXDTI4MDQxNTIx
                    MTQ1NlowKTEZMBcGA1UEBRMQOGFiZTFjNzVkYzFjZDU5OTEMMAoGA1UEDAwDVEVF
                    MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEiejPFIryi9AYM9zV6pGpIFaVpPgdB+zI
                    jJSPgDcZkeDL5Y9Sie0/aAopayrp5UYs+zxRKHIZjAXS9HWOzGg/PIxBa1Fl0tRz
                    HPLGiW1BIrc8pEK+6AJ429QTJoSn4h/No4G2MIGzMB0GA1UdDgQWBBSQv/MlEz9X
                    MxBFdPp9W+RNB1737TAfBgNVHSMEGDAWgBQ2YeEAfIgFCVGLRGxH/xpMyepPEjAP
                    BgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwICBDBQBgNVHR8ESTBHMEWgQ6BB
                    hj9odHRwczovL2FuZHJvaWQuZ29vZ2xlYXBpcy5jb20vYXR0ZXN0YXRpb24vY3Js
                    L0U4RkExOTYzMTREMkZBMTgwDQYJKoZIhvcNAQELBQADggIBAAt4xKMpMK52papq
                    k/QhULlXzaJrYLfLOmvOo1oP4urFcZD7Usk7G2HNoJM2qg3qgjawcUrAov7ZNekl
                    Uc8ZSozY3yJiHGGNRNezXDDPnj+v+XAjE5I46iG3WoZvMYaa0GK5qNMf9v1/UScN
                    D517vhSJIuFjsZjZOHbS83dit7JOoJ/e+5WnLdgdvMpixgkvXrp6Y9DlBFY5c2FY
                    9i1MsPi9WDpylxAigB8g4bcZ0/fsMhLTUiGuenaS4c6TrKaqWBBcTtYdpECNJ+bE
                    FowbW9nxOaI88deIxnldvFpms9XZw/amAx8y2hwoC5kLzhAAcjIidn2oKiTQr1zI
                    C2iVnFWyE8z+T7s//KKfzYLFm5h9YdW7n87j3PkRW5zZOVUzg2a5n/ME8qbCdYQV
                    Qe2ouog4Z/Tu/dwocEeH1jQaiAjqtliiLyJD0XaFqxvGf49IyRFuxq5eK5EAx2Cv
                    GoJLFqL2jIvxdSyOxd58s6I9EnomS6hFWsmwNsuVKxFtnI+Okv1mLm4C8qwVxx6f
                    SQBUJTtUmK/hliL42cjtAfHqVyYM4xV7F8MP10ncEB0hG2yua5g5Y4Ds3wcVIp5Z
                    USgFL9I0raZrb91fJPYetHbb/rrSRp/h805f7Ilf2cApZenzlgcf8JzZ2PuKxWsA
                    a/UNhoh2zVkWkGJr00lp8Df1RYXM
                    -----END CERTIFICATE-----""";

            certs_EC.add(parseCert(str));

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIIFYDCCA0igAwIBAgIJAOj6GWMU0voYMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNV
                    BAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMTYwNTI2MTYyODUyWhcNMjYwNTI0MTYy
                    ODUyWjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0B
                    AQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdS
                    Sxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7
                    tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggj
                    nar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGq
                    C4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQ
                    oVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+O
                    JtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/Eg
                    sTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRi
                    igHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+M
                    RPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9E
                    aDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5Um
                    AGMCAwEAAaOBpjCBozAdBgNVHQ4EFgQUNmHhAHyIBQlRi0RsR/8aTMnqTxIwHwYD
                    VR0jBBgwFoAUNmHhAHyIBQlRi0RsR/8aTMnqTxIwDwYDVR0TAQH/BAUwAwEB/zAO
                    BgNVHQ8BAf8EBAMCAYYwQAYDVR0fBDkwNzA1oDOgMYYvaHR0cHM6Ly9hbmRyb2lk
                    Lmdvb2dsZWFwaXMuY29tL2F0dGVzdGF0aW9uL2NybC8wDQYJKoZIhvcNAQELBQAD
                    ggIBACDIw41L3KlXG0aMiS//cqrG+EShHUGo8HNsw30W1kJtjn6UBwRM6jnmiwfB
                    Pb8VA91chb2vssAtX2zbTvqBJ9+LBPGCdw/E53Rbf86qhxKaiAHOjpvAy5Y3m00m
                    qC0w/Zwvju1twb4vhLaJ5NkUJYsUS7rmJKHHBnETLi8GFqiEsqTWpG/6ibYCv7rY
                    DBJDcR9W62BW9jfIoBQcxUCUJouMPH25lLNcDc1ssqvC2v7iUgI9LeoM1sNovqPm
                    QUiG9rHli1vXxzCyaMTjwftkJLkf6724DFhuKug2jITV0QkXvaJWF4nUaHOTNA4u
                    JU9WDvZLI1j83A+/xnAJUucIv/zGJ1AMH2boHqF8CY16LpsYgBt6tKxxWH00XcyD
                    CdW2KlBCeqbQPcsFmWyWugxdcekhYsAWyoSf818NUsZdBWBaR/OukXrNLfkQ79Iy
                    ZohZbvabO/X+MVT3rriAoKc8oE2Uws6DF+60PV7/WIPjNvXySdqspImSN78mflxD
                    qwLqRBYkA3I75qppLGG9rp7UCdRjxMl8ZDBld+7yvHVgt1cVzJx9xnyGCC23Uaic
                    MDSXYrB4I4WHXPGjxhZuCuPBLTdOLU8YRvMYdEvYebWHMpvwGCF6bAx3JBpIeOQ1
                    wDB5y0USicV3YgYGmi+NZfhA4URSh77Yd6uuJOJENRaNVTzk
                    -----END CERTIFICATE-----""";

            certs_EC.add(parseCert(str));

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIIFETCCAvmgAwIBAgIKEjZQKHdTV5RDkjANBgkqhkiG9w0BAQsFADApMRkwFwYD
                    VQQFExA4YWJlMWM3NWRjMWNkNTk5MQwwCgYDVQQMDANURUUwHhcNMTgwNDE4MjEz
                    MzM3WhcNMjgwNDE1MjEzMzM3WjApMRkwFwYDVQQFExA2MGYyMjlmMTlmY2EzYTA0
                    MQwwCgYDVQQMDANURUUwggGiMA0GCSqGSIb3DQEBAQUAA4IBjwAwggGKAoIBgQDj
                    oKewKGOtMkr7EEYmY+fTpYnqh87E8Z0tWCdfkhlxAxjX2HE0Uxa0sCJ0xZjreHZT
                    ooQvR5NhMDVjnUf+goQ8I9/9t7ITM3njPP9yjMuU+CeBC5VM0RocVry5Xd/nGdC8
                    UMzFb0sQs1L1HvnOVQuOK08vS40P9MAu4GedBbh0/GisY/1vTpQCHc42ABweWxKH
                    vnmgtl79Hq4Qj9zDoMRQHo+hAMs9P+QUet6qkntmPNTfBV4sRgyL+Dlza+TdmfdU
                    e/XSB7Ux9uY4qADWfTVW7TBclLTfwSLNcFR1+4obIhcFcv7/j8rzunSgvc+TB0B+
                    ZYM4znldoxEx4hn/oVaxW9NmrLM9vNw3UQiaEy86xMCcPNZCSjIVwjw/zc4Xbgm8
                    DVVBu8ip0pY9MGD2wV9EWP2bfGv+rsQGOD5UeT0pc+/v8LizN8lLDGnWtvzn1uCY
                    c/cFsMNhGWFf6v4dXSPlmQtJvHUnXSoe4hyD2Vq/JLABNqLXXHeVxyO1FO6qaycC
                    AwEAAaOBujCBtzAdBgNVHQ4EFgQUmbr9kYSB6NcHyyQQCTzuCX8sZQIwHwYDVR0j
                    BBgwFoAUTPvLka8NVNWeiOjUoimnhWNHDLUwDwYDVR0TAQH/BAUwAwEB/zAOBgNV
                    HQ8BAf8EBAMCAgQwVAYDVR0fBE0wSzBJoEegRYZDaHR0cHM6Ly9hbmRyb2lkLmdv
                    b2dsZWFwaXMuY29tL2F0dGVzdGF0aW9uL2NybC8xMjM2NTAyODc3NTM1Nzk0NDM5
                    MjANBgkqhkiG9w0BAQsFAAOCAgEAqVwxi6MFNhvQ3VqvxPwlDniWwd2o/6wPrk3J
                    xtGbYtTi6e++aWDnR8o6SGCsbN+A3bSI2Va2xvkLiS0j3F19SfVdknXEUG9p5Z7M
                    bQ4m1dTH4Ch32r+YVde4UEZmkiMizirrAVct9jkruIPXNrzOHH5rOkMmREdVFcBk
                    g7V67NpXF6JcR5+RNw+mOmZO0MMPE/HviUUkAMXQYPmMhtcFYuYhB4Y6w3+LVWAw
                    kKJ2UBxZjEYih4Db5iWwZDhC1lO6aCNEaC/xcVIIQt0rdwlQ/F9Uz3Zkb+tTGYP7
                    CNB8cxugqlBPphVqIxZcngcPSI2f5/QQ1yV4G7os3p3grKDihXQtI3iyeO0grmkc
                    DzopKfo6WdTl9jcpb0LVUMeVbSXJ9Dfrk3moFeueBOxSMDAWMXHP4Ma4MSBQwViS
                    e4h6D2uL0VefBw8vKhXTCb1VESlGuncNu9PrBF1uFqO6pxUvjjzmijCgrvVbGWiM
                    YPWsToiK40S3DpKL6dI4b3QJM2hvqfhboceD2CO4+i96iR3ODo3ongsGOe2L9UC2
                    V98HvnCy+LClFrk23Laq5WJkVZq2bS5RAfkc4fzf6DGfNpMp/Pj+z12E+FlPBIuQ
                    oeDdHD87ogaMbMug/VrJRPMSO5pDFg9GUQmGjCwI0nFDQcrnWfNxm//3bK/iAmgZ
                    yeUhJXg=
                    -----END CERTIFICATE-----""";

            certs_RSA.add(parseCert(str));

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIIFfzCCA2egAwIBAgIKA4gmZ2BliZaFkDANBgkqhkiG9w0BAQsFADAbMRkwFwYD
                    VQQFExBmOTIwMDllODUzYjZiMDQ1MB4XDTE4MDQxODIxMTQzOFoXDTI4MDQxNTIx
                    MTQzOFowKTEZMBcGA1UEBRMQOGFiZTFjNzVkYzFjZDU5OTEMMAoGA1UEDAwDVEVF
                    MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAz/1/+QlEgDhajc+ZONg6
                    RbbqK3bd5H3QILvCXWB/wWbd/V3cLrdztMh3XM/j34tp5buUFCqQ3TS+JcUSnXe2
                    3Ldc9LCGktAVEYXqTp6JnomS/ZHwx0lDccqBaynghCODeT6okPjFdhnPHJ/smFAC
                    5AtjW6N31IrPi+uJw9hMGew6viG5L4guXqO7wDP+gNYLdmETwYuH7kMzgOYYWHZv
                    6bH4iK4v8QmkC5PX8fyqrQ6Ag59HX0rZP22mGtKGt+12xJeTWc9Nfz+13FbRCIXp
                    WNYARfVCgPI/vpuRvowFnqQMFF8hP0JEdIgP0VZsk39pwLhDH9ASnjwYjq9+j2OX
                    8VIP+ajFl0nRVunjzsrQFTyN4SprkxBUiP8oLl9cYzgd08xRo8xI+3K3mdoK3EVx
                    ufII0KEYckauhjCuGcuztXHrJEucdv5h8gV/jBeXjp1mV7pSPLl4VH0KUqIpWgL/
                    wZT6surci/AT9fN7GW34Nnshwx8yDSUzjpQ1bAclvFlKnQX6neevo2SDAGaW+pA/
                    A4tTF6w4Emf7mnpj5LoXD9bx/5ONmSYRMP2E9+yaJiwX1czqeTj9HzSwm1PIm6qB
                    s+Kjil+Cbgp1EG/gQKsqsKDs3AgQs6JJQxlh3R56tqC0HAy+CTJ5S4rno3TkWim4
                    r1xCat30Ee3+6DPRtIMObFECAwEAAaOBtjCBszAdBgNVHQ4EFgQUTPvLka8NVNWe
                    iOjUoimnhWNHDLUwHwYDVR0jBBgwFoAUNmHhAHyIBQlRi0RsR/8aTMnqTxIwDwYD
                    VR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAgQwUAYDVR0fBEkwRzBFoEOgQYY/
                    aHR0cHM6Ly9hbmRyb2lkLmdvb2dsZWFwaXMuY29tL2F0dGVzdGF0aW9uL2NybC9F
                    OEZBMTk2MzE0RDJGQTE4MA0GCSqGSIb3DQEBCwUAA4ICAQCNewTykPmU/1E9zUDi
                    uhz4V/KzPfPlFNjwzg9eRRXoL0IFkk+8sIl8mdn+hBFkMLU3R3bjTajnVkBdymi+
                    fduwzBD5cdUuVsU/iEbDswg2DQxoj/eOTjNOSMoa9IQXkT9mDriNvDlBKWEQFkDT
                    +2E4kcu00GUQLEnm3tY7uX7EcCIZB9n/j0HMX2kyebB3mwa2NyWtdcR10Bw6XWXu
                    ZTuWPxe+km+x66OqBb5Tq4IMJ5vsqvG5QeureSOBP7BbRQ3z3RBGjIkz485aTdf/
                    Gv3ikbOlEeIfv0tOQTYn4ft2kBdizyuhGRMgqRJcjo5bM4LU2nraJfXVbO7Plnnr
                    6GXLHrHU/G1E/sN2fH4O3Zk/In5Y/y5rMR4WCncPgtO2a8bd7jgpQcU5keCX2anW
                    TtOKtQCCNKn+RBVZAFb7bEEjVEqKBOYqnsiHbml69zX/nnDNFZs5Ek2hQbwGCwVs
                    7Eo4bgwW9mx2WTcRKaIZLk/aJ0pAzQvkPg8ojpJHSZY15AsxgS5k3rWcKIvqXaxk
                    xX0o5ub3V7aV45Sk+/dTV/tgQ9KpXVcNwi19a4knJo/9cbVpBVOYxVf4HZV2ds0l
                    yVPbnJDGKEHtuyO0puIMqMwP8lm8xN6clnVuG+4RuOTmzFN8u9AvlDL92imSyX1v
                    9CyCxsjOYp45mn6Um/+dbYjSZw==
                    -----END CERTIFICATE-----""";

            certs_RSA.add(parseCert(str));

            str = """
                    -----BEGIN CERTIFICATE-----
                    MIIFYDCCA0igAwIBAgIJAOj6GWMU0voYMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNV
                    BAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMTYwNTI2MTYyODUyWhcNMjYwNTI0MTYy
                    ODUyWjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0B
                    AQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdS
                    Sxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7
                    tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggj
                    nar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGq
                    C4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQ
                    oVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+O
                    JtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/Eg
                    sTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRi
                    igHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+M
                    RPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9E
                    aDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5Um
                    AGMCAwEAAaOBpjCBozAdBgNVHQ4EFgQUNmHhAHyIBQlRi0RsR/8aTMnqTxIwHwYD
                    VR0jBBgwFoAUNmHhAHyIBQlRi0RsR/8aTMnqTxIwDwYDVR0TAQH/BAUwAwEB/zAO
                    BgNVHQ8BAf8EBAMCAYYwQAYDVR0fBDkwNzA1oDOgMYYvaHR0cHM6Ly9hbmRyb2lk
                    Lmdvb2dsZWFwaXMuY29tL2F0dGVzdGF0aW9uL2NybC8wDQYJKoZIhvcNAQELBQAD
                    ggIBACDIw41L3KlXG0aMiS//cqrG+EShHUGo8HNsw30W1kJtjn6UBwRM6jnmiwfB
                    Pb8VA91chb2vssAtX2zbTvqBJ9+LBPGCdw/E53Rbf86qhxKaiAHOjpvAy5Y3m00m
                    qC0w/Zwvju1twb4vhLaJ5NkUJYsUS7rmJKHHBnETLi8GFqiEsqTWpG/6ibYCv7rY
                    DBJDcR9W62BW9jfIoBQcxUCUJouMPH25lLNcDc1ssqvC2v7iUgI9LeoM1sNovqPm
                    QUiG9rHli1vXxzCyaMTjwftkJLkf6724DFhuKug2jITV0QkXvaJWF4nUaHOTNA4u
                    JU9WDvZLI1j83A+/xnAJUucIv/zGJ1AMH2boHqF8CY16LpsYgBt6tKxxWH00XcyD
                    CdW2KlBCeqbQPcsFmWyWugxdcekhYsAWyoSf818NUsZdBWBaR/OukXrNLfkQ79Iy
                    ZohZbvabO/X+MVT3rriAoKc8oE2Uws6DF+60PV7/WIPjNvXySdqspImSN78mflxD
                    qwLqRBYkA3I75qppLGG9rp7UCdRjxMl8ZDBld+7yvHVgt1cVzJx9xnyGCC23Uaic
                    MDSXYrB4I4WHXPGjxhZuCuPBLTdOLU8YRvMYdEvYebWHMpvwGCF6bAx3JBpIeOQ1
                    wDB5y0USicV3YgYGmi+NZfhA4URSh77Yd6uuJOJENRaNVTzk
                    -----END CERTIFICATE-----""";

            certs_RSA.add(parseCert(str));


        } catch (Throwable t) {
            XposedBridge.log(t);
            throw new RuntimeException(t);
        }
    }

    private static KeyPair parseKeyPair(String key) throws Throwable {
        Object object;
        try (PEMParser parser = new PEMParser(new StringReader(key))) {
            object = parser.readObject();
        }

        PEMKeyPair pemKeyPair = (PEMKeyPair) object;

        return new JcaPEMKeyConverter().getKeyPair(pemKeyPair);
    }

    private static Certificate parseCert(String cert) throws Throwable {
        PemObject pemObject;
        try (PemReader reader = new PemReader(new StringReader(cert))) {
            pemObject = reader.readPemObject();
        }

        X509CertificateHolder holder = new X509CertificateHolder(pemObject.getContent());

        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static Extension addHackedExtension(Extension extension) {
        try {
            ASN1Sequence keyDescription = ASN1Sequence.getInstance(extension.getExtnValue().getOctets());

            ASN1EncodableVector teeEnforcedEncodables = new ASN1EncodableVector();

            ASN1Sequence teeEnforcedAuthList = (ASN1Sequence) keyDescription.getObjectAt(7).toASN1Primitive();

            for (ASN1Encodable asn1Encodable : teeEnforcedAuthList) {

                ASN1TaggedObject taggedObject = (ASN1TaggedObject) asn1Encodable;

                if (taggedObject.getTagNo() == 704) continue;

                teeEnforcedEncodables.add(taggedObject);
            }

            SecureRandom random = new SecureRandom();

            byte[] bytes1 = new byte[32];
            byte[] bytes2 = new byte[32];

            random.nextBytes(bytes1);
            random.nextBytes(bytes2);

            ASN1Encodable[] rootOfTrustEncodables = {new DEROctetString(bytes1), ASN1Boolean.TRUE, new ASN1Enumerated(0), new DEROctetString(bytes2)};

            ASN1Sequence rootOfTrustSeq = new DERSequence(rootOfTrustEncodables);

            ASN1TaggedObject rootOfTrust = new DERTaggedObject(true, 704, rootOfTrustSeq);

            teeEnforcedEncodables.add(rootOfTrust);

            var attestationVersion = keyDescription.getObjectAt(0);
            var attestationSecurityLevel = keyDescription.getObjectAt(1);
            var keymasterVersion = keyDescription.getObjectAt(2);
            var keymasterSecurityLevel = keyDescription.getObjectAt(3);
            var attestationChallenge = keyDescription.getObjectAt(4);
            var uniqueId = keyDescription.getObjectAt(5);
            var softwareEnforced = keyDescription.getObjectAt(6);
            var teeEnforced = new DERSequence(teeEnforcedEncodables);

            ASN1Encodable[] keyDescriptionEncodables = {attestationVersion, attestationSecurityLevel, keymasterVersion, keymasterSecurityLevel, attestationChallenge, uniqueId, softwareEnforced, teeEnforced};

            ASN1Sequence keyDescriptionHackSeq = new DERSequence(keyDescriptionEncodables);

            ASN1OctetString keyDescriptionOctetStr = new DEROctetString(keyDescriptionHackSeq);

            return new Extension(new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17"), false, keyDescriptionOctetStr);

        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        return extension;
    }

    private static Extension createHackedExtensions() {
        try {
            SecureRandom random = new SecureRandom();

            byte[] bytes1 = new byte[32];
            byte[] bytes2 = new byte[32];

            random.nextBytes(bytes1);
            random.nextBytes(bytes2);

            ASN1Encodable[] rootOfTrustEncodables = {new DEROctetString(bytes1), ASN1Boolean.TRUE, new ASN1Enumerated(0), new DEROctetString(bytes2)};

            ASN1Sequence rootOfTrustSeq = new DERSequence(rootOfTrustEncodables);

            ASN1Integer[] purposesArray = {new ASN1Integer(0), new ASN1Integer(1), new ASN1Integer(2), new ASN1Integer(3), new ASN1Integer(4), new ASN1Integer(5)};

            ASN1Encodable[] digests = {new ASN1Integer(1), new ASN1Integer(2), new ASN1Integer(3), new ASN1Integer(4), new ASN1Integer(5), new ASN1Integer(6)};

            var Apurpose = new DERSet(purposesArray);
            var Aalgorithm = new ASN1Integer(3);
            var AkeySize = new ASN1Integer(256);
            var Adigest = new DERSet(digests);
            var AecCurve = new ASN1Integer(1);
            var AnoAuthRequired = DERNull.INSTANCE;
            var AosVersion = new ASN1Integer(130000);
            var AosPatchLevel = new ASN1Integer(202401);
            var AcreationDateTime = new ASN1Integer(System.currentTimeMillis());
            var Aorigin = new ASN1Integer(0);

            var purpose = new DERTaggedObject(true, 1, Apurpose);
            var algorithm = new DERTaggedObject(true, 2, Aalgorithm);
            var keySize = new DERTaggedObject(true, 3, AkeySize);
            var digest = new DERTaggedObject(true, 5, Adigest);
            var ecCurve = new DERTaggedObject(true, 10, AecCurve);
            var noAuthRequired = new DERTaggedObject(true, 503, AnoAuthRequired);
            var creationDateTime = new DERTaggedObject(true, 701, AcreationDateTime);
            var origin = new DERTaggedObject(true, 702, Aorigin);
            var rootOfTrust = new DERTaggedObject(true, 704, rootOfTrustSeq);
            var osVersion = new DERTaggedObject(true, 705, AosVersion);
            var osPatchLevel = new DERTaggedObject(true, 706, AosPatchLevel);

            ASN1Encodable[] teeEnforcedEncodables = {purpose, algorithm, keySize, digest, ecCurve, noAuthRequired, creationDateTime, origin, rootOfTrust, osVersion, osPatchLevel};

            ASN1Integer attestationVersion = new ASN1Integer(4);
            ASN1Enumerated attestationSecurityLevel = new ASN1Enumerated(1);
            ASN1Integer keymasterVersion = new ASN1Integer(41);
            ASN1Enumerated keymasterSecurityLevel = new ASN1Enumerated(1);
            ASN1OctetString attestationChallenge = new DEROctetString(attestationChallengeBytes);
            ASN1OctetString uniqueId = new DEROctetString("".getBytes());
            ASN1Sequence softwareEnforced = new DERSequence();
            ASN1Sequence teeEnforced = new DERSequence(teeEnforcedEncodables);

            ASN1Encodable[] keyDescriptionEncodables = {attestationVersion, attestationSecurityLevel, keymasterVersion, keymasterSecurityLevel, attestationChallenge, uniqueId, softwareEnforced, teeEnforced};

            ASN1Sequence keyDescriptionHackSeq = new DERSequence(keyDescriptionEncodables);

            ASN1OctetString keyDescriptionOctetStr = new DEROctetString(keyDescriptionHackSeq);

            return new Extension(new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17"), false, keyDescriptionOctetStr);

        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        return null;
    }

    private static Certificate createLeafCert() {
        try {
            long now = System.currentTimeMillis();
            Date notBefore = new Date(now);

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(notBefore);
            calendar.add(Calendar.HOUR, 1);

            Date notAfter = calendar.getTime();

            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(new X500Name("CN=chiteroman"), BigInteger.ONE, notBefore, notAfter, new X500Name("CN=Android Keystore Key"), keyPair_EC.getPublic());

            KeyUsage keyUsage = new KeyUsage(KeyUsage.keyCertSign);
            certBuilder.addExtension(Extension.keyUsage, true, keyUsage);

            certBuilder.addExtension(createHackedExtensions());

            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair_EC.getPrivate());

            X509CertificateHolder certHolder = certBuilder.build(contentSigner);

            return new JcaX509CertificateConverter().getCertificate(certHolder);

        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        return null;
    }

    private static Certificate hackLeafExistingCert(Certificate certificate) {
        try {
            X509CertificateHolder certificateHolder = new X509CertificateHolder(certificate.getEncoded());

            KeyPair keyPair;
            if (KeyProperties.KEY_ALGORITHM_EC.equals(certificate.getPublicKey().getAlgorithm())) {
                keyPair = keyPair_EC;
            } else {
                keyPair = keyPair_RSA;
            }

            long now = System.currentTimeMillis();
            Date notBefore = new Date(now);

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(notBefore);
            calendar.add(Calendar.HOUR, 1);

            Date notAfter = calendar.getTime();

            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(certificateHolder.getIssuer(), certificateHolder.getSerialNumber(), notBefore, notAfter, certificateHolder.getSubject(), keyPair.getPublic());

            for (Object extensionOID : certificateHolder.getExtensionOIDs()) {

                ASN1ObjectIdentifier identifier = (ASN1ObjectIdentifier) extensionOID;

                if ("1.3.6.1.4.1.11129.2.1.17".equals(identifier.getId())) continue;

                certBuilder.addExtension(certificateHolder.getExtension(identifier));
            }

            Extension extension = certificateHolder.getExtension(new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17"));

            certBuilder.addExtension(addHackedExtension(extension));

            ContentSigner contentSigner;
            if (KeyProperties.KEY_ALGORITHM_EC.equals(certificate.getPublicKey().getAlgorithm())) {
                contentSigner = new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate());
            } else {
                contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            }

            X509CertificateHolder certHolder = certBuilder.build(contentSigner);

            return new JcaX509CertificateConverter().getCertificate(certHolder);

        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        return certificate;
    }

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {

        final var systemFeatureHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String featureName = (String) param.args[0];

                if (PackageManager.FEATURE_STRONGBOX_KEYSTORE.equals(featureName))
                    param.setResult(Boolean.FALSE);
                else if (PackageManager.FEATURE_KEYSTORE_APP_ATTEST_KEY.equals(featureName))
                    param.setResult(Boolean.FALSE);
                else if ("android.software.device_id_attestation".equals(featureName))
                    param.setResult(Boolean.FALSE);
            }
        };

        try {
            Application app = AndroidAppHelper.currentApplication();

            Class<?> PackageManagerClass, SharedPreferencesClass;

            if (app == null) {
                PackageManagerClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", lpparam.classLoader);
                SharedPreferencesClass = XposedHelpers.findClass("android.app.SharedPreferencesImpl", lpparam.classLoader);
            } else {
                PackageManagerClass = app.getPackageManager().getClass();
                SharedPreferencesClass = app.getSharedPreferences("settings", Context.MODE_PRIVATE).getClass();
            }

            XposedHelpers.findAndHookMethod(PackageManagerClass, "hasSystemFeature", String.class, systemFeatureHook);
            XposedHelpers.findAndHookMethod(PackageManagerClass, "hasSystemFeature", String.class, int.class, systemFeatureHook);

            XposedHelpers.findAndHookMethod(SharedPreferencesClass, "getBoolean", String.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String key = (String) param.args[0];

                    if ("prefer_attest_key".equals(key)) param.setResult(Boolean.FALSE);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            XposedHelpers.findAndHookMethod(KeyGenParameterSpec.Builder.class, "setAttestationChallenge", byte[].class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    attestationChallengeBytes = (byte[]) param.args[0];
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            KeyPairGeneratorSpi keyPairGeneratorSpi_EC = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            XposedHelpers.findAndHookMethod(keyPairGeneratorSpi_EC.getClass(), "generateKeyPair", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return keyPair_EC;
                }
            });
            KeyPairGeneratorSpi keyPairGeneratorSpi_RSA = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            XposedHelpers.findAndHookMethod(keyPairGeneratorSpi_RSA.getClass(), "generateKeyPair", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return keyPair_RSA;
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            KeyStoreSpi keyStoreSpi = (KeyStoreSpi) XposedHelpers.getObjectField(keyStore, "keyStoreSpi");
            XposedHelpers.findAndHookMethod(keyStoreSpi.getClass(), "engineGetCertificateChain", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Certificate[] certificates = null;

                    try {
                        certificates = (Certificate[]) param.getResultOrThrowable();
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }

                    LinkedList<Certificate> certificateList = new LinkedList<>();

                    if (certificates == null) {

                        certificateList.addAll(certs_EC);
                        certificateList.addFirst(createLeafCert());

                    } else {
                        if (!(certificates[0] instanceof X509Certificate x509Certificate)) return;

                        byte[] bytes = x509Certificate.getExtensionValue("1.3.6.1.4.1.11129.2.1.17");

                        if (bytes == null || bytes.length == 0) return;

                        String algorithm = x509Certificate.getPublicKey().getAlgorithm();
                        if (KeyProperties.KEY_ALGORITHM_EC.equals(algorithm)) {

                            certificateList.addAll(certs_EC);

                        } else if (KeyProperties.KEY_ALGORITHM_RSA.equals(algorithm)) {

                            certificateList.addAll(certs_RSA);
                        }
                        certificateList.addFirst(hackLeafExistingCert(x509Certificate));
                    }

                    param.setResult(certificateList.toArray(new Certificate[0]));
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
