import ecdsa
import hashlib
import random

import base58
import binascii
import multiprocessing
import sys
from ecdsa import SigningKey, VerifyingKey
from ecdsa.ellipticcurve import Point
from functools import reduce

class RingSig:
    G = ecdsa.SECP256k1.generator

    def __init__(self, keys, borromean_parent=None):
        """
        Keys is an array of tuples. Each tuple is (privkey, Pubkey). All but 1 privKey is None, since we know
        only one private key.
        """
        self.pubkeys = list(map(lambda k: k[1], keys))
        self.privkey = list(filter(lambda k: k[0] is not None, keys))
        if len(self.privkey) == 0:
            pass
        elif len(self.privkey) != 1:
            raise ValueError("Exactly one of the keys should have a private key")
        else:
            self.privkey = self.privkey[0][0]
            # Identify the public key that has the private key
            self.j = list(map(lambda k: k[0], keys)).index(self.privkey)

        self.ring_size = len(keys)

        # In case this is a part of a borromean signature
        self.borromean_parent = borromean_parent

    def do_aos_hashstep(self, p: ecdsa.ellipticcurve.Point, msg: bytes):
        hasher = hashlib.sha256()
        hasher.update(p.x().to_bytes(32, "big"))
        hasher.update(p.y().to_bytes(32, "big"))
        hasher.update(msg)
        return int.from_bytes(hasher.digest(), "big")

    def sign(self, msg: bytes):
        e_values = [0] * self.ring_size
        s_values = [0] * self.ring_size

        # Start signing at j + 1. The first step is special, since it contains a.G instead of s=s.G+ex.Px
        a = SigningKey.generate(curve=ecdsa.SECP256k1).privkey.secret_multiplier
        if self.borromean_parent is None or (self.j+1) % self.ring_size is not 0:
            e_values[(self.j+1) % self.ring_size] = self.do_aos_hashstep(a * self.G, msg)
        else:
            e_values[(self.j+1) % self.ring_size] = self.borromean_parent.do_e0_step_sign(a * self.G, msg)

        # The, for the rest of the ring, calculate e_value = s.G + ex
        start_at = (self.j+2) % self.ring_size
        for i in range(start_at, start_at + self.ring_size-1):
            cur_i, prev_i = i % self.ring_size, (i - 1) % self.ring_size

            s_values[prev_i] = SigningKey.generate(curve=ecdsa.SECP256k1).privkey.secret_multiplier
            hashstep_point = (s_values[prev_i] * self.G) + (e_values[prev_i] * self.pubkeys[prev_i])
            if self.borromean_parent is None or cur_i is not 0:
                e_values[cur_i] = self.do_aos_hashstep(hashstep_point, msg)
            else:
                e_values[cur_i] = self.borromean_parent.do_e0_step_sign(hashstep_point, msg)

        s_values[self.j] = (a - e_values[self.j] * self.privkey) % ecdsa.SECP256k1.generator.order()

        # Check for e2 consistency. Temporarily disabled for borromean sigs, because I don't understand how it works for that.
        if self.borromean_parent is None:
            ej_prime = self.do_aos_hashstep((s_values[self.j] * self.G) + (e_values[self.j] * self.pubkeys[self.j]), msg)
            assert e_values[(self.j+1) % self.ring_size] == ej_prime

        signature = [e_values[0]] + s_values  # (e0, s0, s1, s2)
        return signature

    def verify(self, msg: bytes, sig):
        assert len(sig) == self.ring_size + 1

        e_values = [0] * self.ring_size
        e_values[0] = sig[0]
        s_values = sig[1:]

        for i in range(1, self.ring_size):
            prev_i = (i - 1) % self.ring_size
            e_values[i] = self.do_aos_hashstep((s_values[prev_i] * self.G) + (e_values[prev_i] * self.pubkeys[prev_i]), msg)

        hashpoint = (s_values[-1] * self.G) + (e_values[-1] * self.pubkeys[-1])
        if self.borromean_parent is None:
            e0_derived = self.do_aos_hashstep(hashpoint, msg)
        else:
            e0_derived = self.borromean_parent.do_e0_step_verify(hashpoint, msg)

        return e_values[0] == e0_derived


class BorromeanRingSig:
    def __init__(self, keys):
        """
        Keys is an array of array of tuples.
        The first array is the number of rings.
        For each ring, there's an array of tuples.
        Each tuple is (privkey, Pubkey). All but 1 privKey is None, since we know
        only one private key.
        """
        self.num_rings = len(keys)
        self.rings = []

        # For each ring, create a RingSig
        for i in range(self.num_rings):
            self.rings.append(RingSig(keys[i], self))

        # Initialize variables for signing
        self.current_ring = 0
        self.R_values = []
        self.signatures = []
        self.e0 = 0
        self.verification_sigs = []
        self.verification_sig_e0 = 0
        self.verifications = []

    def sign(self, msg: bytes):
        """
        Sign by calling the first ring. The first ring will then call do_e0_step, at which point we'll continue the signing
        """
        self.current_ring = 0
        self.R_values = []
        self.e0 = 0

        # Start signing at ring number 0
        self.signatures.append(self.rings[0].sign(msg))

        # Note that the signatures will be filled in backwards, so we need to reverse them.
        sigs = list(reversed(self.signatures))
        unified_sigs = []

        # All the e0s are the same (we hope).
        all_e0_values = list(map(lambda s: s[0], sigs))
        for i in range(self.num_rings):
            assert all_e0_values[0] == all_e0_values[i]
            unified_sigs.append(sigs[i][1:])

        return [all_e0_values[0]] + unified_sigs

    def verify(self, msg: bytes, sig):
        """
        Verify the signature in each ring
        """
        self.current_ring = 0
        self.R_values = []
        self.verifications = []
        self.verification_sigs = sig

        # extract the e0
        self.verification_sig_e0 = self.verification_sigs[0]
        # And move the signatures 1, so that verification_sig[i] is the signature for i
        self.verification_sigs = self.verification_sigs[1:]
        verified = self.rings[0].verify(msg, [self.verification_sig_e0] + self.verification_sigs[0])
        self.verifications.append(verified)

        return list(reversed(self.verifications))

    def do_e0_step_verify(self, hash_point: ecdsa.ellipticcurve.Point, msg: bytes):
        self.R_values.append(hash_point)
        self.current_ring += 1

        if self.current_ring == self.num_rings:
            # Calculate the single e0
            hasher = hashlib.sha256()
            for i in range(len(self.R_values)):
                hasher.update(self.R_values[i].x().to_bytes(32, "big"))
                hasher.update(self.R_values[i].y().to_bytes(32, "big"))

            # Finally, the message
            hasher.update(msg)
            # Return the e0
            self.e0 = int.from_bytes(hasher.digest(), "big")
        else:
            verified = self.rings[self.current_ring].verify(msg, [self.verification_sig_e0] + self.verification_sigs[self.current_ring])
            self.verifications.append(verified)

        return self.e0

    def do_e0_step_sign(self, hash_point: ecdsa.ellipticcurve.Point, msg: bytes):
        # The current_ring is calling to get the unified e0 value. We store this ring's R value and then move to
        #  the next one
        self.R_values.append(hash_point)
        self.current_ring += 1

        if self.current_ring == self.num_rings:
            # Calculate the single e0
            hasher = hashlib.sha256()
            for i in range(len(self.R_values)):
                hasher.update(self.R_values[i].x().to_bytes(32, "big"))
                hasher.update(self.R_values[i].y().to_bytes(32, "big"))

            # Finally, the message
            hasher.update(msg)
            # Return the e0
            self.e0 = int.from_bytes(hasher.digest(), "big")
        else:
            self.signatures.append(self.rings[self.current_ring].sign(msg))

        return self.e0

def negate_point(p: ecdsa.ellipticcurve.Point):
    return ecdsa.ellipticcurve.Point(p.curve(), p.x(), -p.y() % p.curve().p())

class RangeProof:
    def __init__(self):
        """
        Prove that v is representable in k bits
        """
        self.G = ecdsa.SECP256k1.generator
        self.H = self.G * 100
        self.msg = "signature".encode("UTF-8")

    def pretty_print(self, proof):
        """
        Range proof is of the form:
        P0, P1...k,e0,(s0,s1),(s0',s1')...k
        So, a total of k + 1 + k elements.
        """
        k = int((len(proof) - 1) / 2)
        assert k + 1 + k == len(proof)
        for i in range(k):
            print("P{}: {}".format(i, proof[i]))
        print("e0: {}".format(proof[k]))
        for i in range(k+1, 2*k+1):
            print("[s0: {}, s1: {}]".format(proof[i][0], proof[i][1]))

    def get_range_proof(self, v: int, k: int, a: Point):
        # First, get the pederson commitment P = a.G + v.H
        if a is None:
            a = random.randint(1, ecdsa.SECP256k1.generator.order())
        P = (a * self.G) + (v * self.H)

        # Next, break down "v" into bit representation
        b_values = []
        while v > 0:
            b_values.append(v & 1)
            v = v >> 1

        assert k >= len(b_values)

        # Then, fill the rest of the "b" values with 0s
        b_values += [0] * (k - len(b_values))

        a_values = []
        P_values = []
        rings = []
        # Step 3, for each "b" value, generate the P_i = a_i.G + 2^i.b_i.H
        for i in range(k):
            if i < (k-1):
                a_values.append(random.randint(1, ecdsa.SECP256k1.generator.order()))
            else:
                a_values.append(a - sum(a_values))
            P_i = (a_values[i] * self.G) + (2**i * b_values[i] * self.H)
            P_values.append(P_i)

            ring_elements = []
            P_i_other = P_i + negate_point(2**i * self.H)
            if b_values[i] == 0:
                ring_elements.append((a_values[i], P_i))
                ring_elements.append((None, P_i_other))
            else:
                ring_elements.append((None, P_i))
                ring_elements.append((a_values[i], P_i_other))

            rings.append(ring_elements)

        # Assert P = sum(P_values)
        assert P == reduce(lambda p1, p2: p1 + p2, P_values[1:], P_values[0])

        signer = BorromeanRingSig(rings)
        sign = signer.sign(self.msg)
        assert signer.verify(self.msg, sign) == [True] * k

        return P_values + sign

    def verify_range_proof(self, commitment: Point, proof, k: int):
        """
        The proof is of the form [P0,P1...Pn, e0, [s0, s1], [s0', s1'],...]
        """
        # First, make sure that the commitment matches the sum of Ps
        P_Total = proof[0]
        for i in range(1, k):
            P_Total = P_Total + proof[i]
        if not P_Total == commitment:
            return False

        # Construct Borromean verifier
        keys = []
        for i in range(k):
            p = proof[i]
            p_other = p + negate_point(2**i * self.H)
            keys.append([(None, p), (None, p_other)])
        sig = proof[k:]
        verifier = BorromeanRingSig(keys)
        return verifier.verify(self.msg, sig)

def get_new_ring_keys(ring_size: int, j: int):
    keys = []
    # Generate a bunch of keys
    for i in range(ring_size):
        k = SigningKey.generate(curve=ecdsa.SECP256k1)
        if i == j:
            keys.append((k.privkey.secret_multiplier, k.get_verifying_key().pubkey.point))
        else:
            keys.append((None, k.get_verifying_key().pubkey.point))

    return keys

if __name__ == '__main__':
    rp = RangeProof()
    k = 3
    proof = rp.get_range_proof(4, k)
    print("Range proof: {}".format(rp.verify_range_proof(proof, k)))

    # The index of the private key we know
    j = 0
    ring_size = 2
    keys = get_new_ring_keys(ring_size, j)

    r = RingSig(keys)

    msg = "Hello World".encode('UTF-8')
    sig = r.sign(msg)
    print("Single ring: {}".format(r.verify(msg, sig)))

    # Test borromean signature
    # Start by generating a bunch of keys
    num_rings = 4
    ring_size = 2
    keys = []
    for i in range(num_rings):
        keys.append(get_new_ring_keys(ring_size, 1))

    br = BorromeanRingSig(keys)
    sig = br.sign(msg)
    print("Borromean ring sig: {}".format(br.verify(msg, sig)))
