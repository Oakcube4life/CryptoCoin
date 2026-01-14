/*
 * Gavin MacFadyen
 *
 * Util for creating the Keys for the genesis block.
 */
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import java.util.Base64;

public class GenesisUtil {
    //Run this to get the genesis blocks public key.
    /*public static void main (String[] args) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        String pub = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        String priv = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());

        System.out.println("PUBLIC KEY:");
        System.out.println(pub);
        System.out.println("\nPRIVATE KEY (save only if spendable):");
        System.out.println(priv);
    }*/

    public static final PublicKey GENESIS_PUBLIC_KEY = loadPublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn3cS+KWiRFcOMnBqHkqkzmaHYSOW/LFTsHFqFyKXEz3IXhoz5xaV94+3WETS9YKVowQo6EaWxj9/eFP7vDaxDlMZ5/srdgf/p0bMlxRtxs0/pTnjH4MxRM9RcYnNXDWnUYl9WfiAW/L2g/XN1Mtz2XWYe6M9QBDKcJq3MVB+cDr3ERNew665qA5XBNmiV1l0jDn1o21JgqyEzky6lR5k2npxh7F21HnNvVD5nehPAM/NIpq9X8AkVP5NqHpCs9DOERrIpuZ0yR69z6QD7SpacWU6pl4TMJ98k1xPOowYZhI5cM2SSx5NzuzU44VCowrt3Zdu8x6e1saTuDPKUYflKwIDAQAB");

    private static PublicKey loadPublicKey(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
