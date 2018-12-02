import ecdsa
import hashlib
import random
import base58
import binascii

from ecdsa import SigningKey, VerifyingKey
from ecdsa.ellipticcurve import Point
from cryptoaccounts import Account, ViewAccount
from ringsignatures import RangeProof, negate_point

k = 8

class Contract:
    # Balances for every account
    balances = dict()

    def do_deposit(self, addr: str, deposit_commitment: Point):
        """
        Deposit amount into the contract.
        TODO: This is not throught through. How to hide, verify etc...
        """
        assert self.balances.get(addr) is None
        self.balances[addr] = deposit_commitment
        print("op: Deposit, addr: {}, commitment: {}".format(addr, deposit_commitment))


class Mixer:
    pending_transactions = []

    """
    This is the mixer that mixes the transactions and publishes it to the contract
    """
    def send(self, from_addr: str, to_addr: str,
             T: Point, t_range_proof,
             s: int, fee_blinding_factor: int,
             Ba_new: Point, Ba_new_range_proof,
             EPH: Point):
        # TODO: Add the ephemeral ECDH key / pass on bliding factor stuff

        # First, mixer has to calculate fees and get the equation right
        # T = S + F
        f = 1
        S = s * self.G
        F = f * fee_blinding_factor
        assert T == S + F

        # Next, make sure BB_new is accurate
        BB_new = contract.balances.get(to_addr) + S

        # Verify range proofs
        prover = RangeProof()
        assert prover.verify_range_proof(T, t_range_proof, k)
        assert prover.verify_range_proof(Ba_new, Ba_new_range_proof, k)

        # Calculate the destination hash
        hasher = hashlib.sha256()
        hasher.update(to_addr.encode("UTF-8"))
        hasher.update(S.x())
        hasher.update(S.y())
        destination_hash = hasher.digest()

        self.pending_transactions.append((from_addr, T, t_range_proof, Ba_new, Ba_new_range_proof, destination_hash))

    def execute(self):
        """
        Pop all the pending transactions and execute them by sending to contract
        """
        c_args = []
        c_args.push()

class Alice:
    blinding_factor = 0
    known_balance = 0

    G = ecdsa.SECP256k1.generator
    H = G * 100

    def __init__(self, contract: Contract, mixer: Mixer, privkey_wif=None):
        self.contract = contract
        self.mixer = mixer
        self.account = Account.from_wif(privkey_wif) if privkey_wif is not None else Account()

    def get_privkey(self):
        return self.account.get_secret()

    def get_pubkey_point(self):
        return self.account.get_pubkey_point()

    def get_address(self):
        return self.account.get_address()

    def deposit(self, amount: int):
        """
        Deposit some ether
        """
        # Step 1, create a blinding factor
        self.blinding_factor = random.randint(1, ecdsa.SECP256k1.generator.order())
        deposit_commitment = self.blinding_factor * self.G + amount * self.H
        self.known_balance = amount

        self.contract.do_deposit(self.get_address(), deposit_commitment)
        assert self.verify_known_balance() is True

    def verify_known_balance(self):
        """
        Verify if our known balance matches what the contract has.
        """
        expected = self.blinding_factor * self.G + self.known_balance * self.H
        return contract.balances[self.get_address()] == expected

    def send(self, to_addr: str, t: int):
        """
        Send an amount 't' to 'to_addr'
        """
        assert self.verify_known_balance() is True
        assert self.known_balance >= t

        # Generate alpha
        alpha = random.randint(1, ecdsa.SECP256k1.generator.order())
        T = alpha * self.G + t * self.H
        prover = RangeProof()

        t_range_proof = prover.get_range_proof(t, k, alpha)
        assert prover.verify_range_proof(T, t_range_proof, k)
        print("t_range_proof")
        prover.pretty_print(t_range_proof)

        B_new = contract.balances[self.get_address()] + negate_point(T)
        new_balance = self.known_balance - t
        new_blinding_factor = self.blinding_factor - alpha
        B_new_range_proof = prover.get_range_proof(new_balance, 8, new_blinding_factor)
        assert prover.verify_range_proof(B_new, B_new_range_proof, k)
        print("new_balance_range_proof")
        prover.pretty_print(B_new_range_proof)

        # Generate the ephemeral key, and derive a beta
        eph = random.randint(1, ecdsa.SECP256k1.generator.order())
        EPH = eph * self.G

        mixer.send(self.get_address(), to_addr,
                   T, t_range_proof,
                   t-1, alpha, # should ideally be (alpha-beta), but beta is 0
                   B_new, B_new_range_proof,
                   EPH)

        self.blinding_factor = self.blinding_factor - alpha
        self.known_balance = self.known_balance - t

class Chris:
    pass

if __name__ == '__main__':
    contract = Contract()
    mixer = Mixer()
    a = Alice(contract, mixer, privkey_wif="5KTFbGvR5hyWr5B2YQHbuXkLyyjKNLEwqKoyz1UTiC8X2SCWkY7")

    a.deposit(5)
    a.send("other", 2)