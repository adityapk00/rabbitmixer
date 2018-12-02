package com.rabbitmixer.ethmixer.web3.commands.data;

import org.bouncycastle.jcajce.provider.digest.SHA256;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public class StealthTransferData {
        public static class Point {
            public BigInteger x;
            public BigInteger y;

            public Point(BigInteger x, BigInteger y) {
                this.x = x;
                this.y = y;
            }

            public Point(List<BigInteger> xy) {
                this.x = xy.get(0);
                this.y = xy.get(1);
            }

            public List<BigInteger> toList() {
                return Arrays.asList(x, y);
            }
        }

        public static class Signature {
            public BigInteger r;
            public BigInteger s;
            public BigInteger v;

            public Signature(BigInteger v, BigInteger r, BigInteger s) {
                this.v = v;
                this.r = r;
                this.s = s;
            }
        }

        public Point S;
        public Point T;
        public Point F;
        public Point eph_pub;

        public BigInteger t_secret;
        public BigInteger s_secret;

        public BigInteger f_blinding_factor;

        public String from_address;
        public List<BigInteger> new_bal_range_proof;

        public String reciever_hash;
        public BigInteger token_nonce;

        public Signature signature;
        public List<BigInteger> t_range_proof;
        public List<BigInteger> s_range_proof;

        public BigInteger s_amount;
        public String to_address;

        public boolean isTransparent() {
            if (s_amount == null) return false;
            if (s_amount.compareTo(new BigInteger("0")) == 0) return false;
            return true;
        }

        public String hash() {
            SHA256.Digest sha256 = new SHA256.Digest();
            sha256.update(token_nonce.toByteArray());
            sha256.update(from_address.getBytes());
            sha256.update(to_address.getBytes());
            sha256.update(signature.r.toByteArray());
            sha256.update(signature.s.toByteArray());
            return new BigInteger(1, sha256.digest()).toString(16);
        }

        // Everything below this shouldn't be sent, but is being sent for debugging purposes.
        public BigInteger debug_s;              // For the reciever to see how much money he got. Should be encrypted
        public BigInteger debug_f;              // We should already know this.
        public BigInteger debug_s_blinding;     // For the reciever. Should be derivable from eph_pub * pk
        public BigInteger debug_new_balance;    // For sender's own history. Should be encrypted
        public BigInteger debug_t_blinding;
        public BigInteger debug_t;
}
