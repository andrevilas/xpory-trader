package cpservice

import grails.gorm.transactions.Transactional

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Transactional
class JwtKeyService {

    static final int DEFAULT_KEY_ROTATION_DAYS = 90

    int keyRotationDays = DEFAULT_KEY_ROTATION_DAYS

    WhiteLabelSigningKey ensureActiveKey(String whiteLabelId) {
        WhiteLabel whiteLabel = WhiteLabel.get(whiteLabelId)
        if (!whiteLabel) {
            throw new IllegalArgumentException('Unknown white label id')
        }
        WhiteLabelSigningKey key = WhiteLabelSigningKey.findByWhiteLabelAndActive(whiteLabel, true)
        if (key && key.validUntil && key.validUntil.before(new Date())) {
            key.active = false
            key.save(flush: true)
            key = null
        }
        if (!key) {
            key = generateKeyPair(whiteLabel)
        }
        return key
    }

    List<WhiteLabelSigningKey> listActiveKeys() {
        WhiteLabelSigningKey.findAllByActive(true)
    }

    WhiteLabelSigningKey findByKeyId(String keyId) {
        if (!keyId) {
            return null
        }
        WhiteLabelSigningKey.findByKeyId(keyId)
    }

    WhiteLabelSigningKey rotateKeyFor(String whiteLabelId) {
        WhiteLabel whiteLabel = WhiteLabel.get(whiteLabelId)
        if (!whiteLabel) {
            throw new IllegalArgumentException('Unknown white label id')
        }
        WhiteLabelSigningKey existing = WhiteLabelSigningKey.findByWhiteLabelAndActive(whiteLabel, true)
        if (existing) {
            existing.active = false
            existing.validUntil = existing.validUntil ?: new Date()
            existing.save(flush: true)
        }
        generateKeyPair(whiteLabel)
    }

    int rotateAllActiveKeys() {
        List<WhiteLabel> whiteLabels = WhiteLabel.list()
        int rotated = 0
        whiteLabels.each { wl ->
            rotateKeyFor(wl.id)
            rotated++
        }
        rotated
    }

    PrivateKey toPrivateKey(WhiteLabelSigningKey key) {
        byte[] pkcs8 = Base64.getDecoder().decode(key.privateKey)
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8)
        KeyFactory keyFactory = KeyFactory.getInstance('RSA')
        keyFactory.generatePrivate(spec)
    }

    PublicKey toPublicKey(WhiteLabelSigningKey key) {
        byte[] x509 = Base64.getDecoder().decode(key.publicKey)
        X509EncodedKeySpec spec = new X509EncodedKeySpec(x509)
        KeyFactory keyFactory = KeyFactory.getInstance('RSA')
        keyFactory.generatePublic(spec)
    }

    Map<String, Object> asJwk(WhiteLabelSigningKey key) {
        RSAPublicKey rsa = (RSAPublicKey) toPublicKey(key)
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding()
        Map<String, Object> jwk = [
                kty: 'RSA',
                use: 'sig',
                alg: key.algorithm,
                kid: key.keyId,
                n  : encoder.encodeToString(toUnsignedBytes(rsa.getModulus().toByteArray())),
                e  : encoder.encodeToString(toUnsignedBytes(rsa.getPublicExponent().toByteArray()))
        ]
        if (key.validUntil) {
            jwk.exp = key.validUntil.toInstant().epochSecond
        }
        return jwk
    }

    protected WhiteLabelSigningKey generateKeyPair(WhiteLabel whiteLabel) {
        KeyPair pair = newRsaKeyPair()
        String keyId = UUID.randomUUID().toString()
        Date now = new Date()
        Date expires = Date.from(Instant.now().plusSeconds(keyRotationDays * 24l * 3600l))
        WhiteLabelSigningKey key = new WhiteLabelSigningKey(
                whiteLabel : whiteLabel,
                keyId      : keyId,
                algorithm  : 'RS256',
                publicKey  : Base64.getEncoder().encodeToString(pair.public.encoded),
                privateKey : Base64.getEncoder().encodeToString(pair.private.encoded),
                active     : true,
                validFrom  : now,
                validUntil : expires
        )
        key.save(failOnError: true, flush: true)
        return key
    }

    protected KeyPair newRsaKeyPair() {
        KeyPairGenerator generator = KeyPairGenerator.getInstance('RSA')
        generator.initialize(2048, new SecureRandom())
        generator.generateKeyPair()
    }

    private byte[] toUnsignedBytes(byte[] input) {
        if (input.length == 0) {
            return input
        }
        if (input[0] == 0x00) {
            byte[] truncated = new byte[input.length - 1]
            System.arraycopy(input, 1, truncated, 0, truncated.length)
            return truncated
        }
        return input
    }
}
