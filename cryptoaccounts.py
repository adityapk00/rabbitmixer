import ecdsa
import hashlib
import base58
import binascii
import multiprocessing
import sys
from ecdsa import SigningKey, VerifyingKey


class ViewAccount:
    def __init__(self, pub_point: ecdsa.ellipticcurve.Point):
        self.verifying_key = VerifyingKey.from_public_point(pub_point, ecdsa.SECP256k1)

    @staticmethod
    def from_pubkey(pubkey: str):
        pk_bytes = binascii.unhexlify(pubkey)
        x, y = int.from_bytes(pk_bytes[-64:-32], "big"), int.from_bytes(pk_bytes[-32:], "big")
        return ViewAccount(ecdsa.ellipticcurve.Point(ecdsa.SECP256k1.curve, x, y))

    @staticmethod
    def from_pubkey_point(point: ecdsa.ellipticcurve.Point):
        return ViewAccount(point)

    def to_pubkey_bytes(self):
        vk = self.verifying_key
        x, y = vk.pubkey.point.x(), vk.pubkey.point.y()

        # Pack as 0x04 + x + y
        raw_bytes = bytes.fromhex('04') + x.to_bytes(32, "big") + y.to_bytes(32, "big")
        return raw_bytes

    def get_address(self):
        raw_bytes = self.to_pubkey_bytes()

        # 0x00 + ripemp160(sha256(packed bytes))
        hashed = bytes.fromhex('00') + hashlib.new("ripemd160", hashlib.sha256(raw_bytes).digest()).digest()

        # base58 + checksum
        return base58.b58encode_check(hashed)

    def get_pubkey(self):
        return binascii.hexlify(self.to_pubkey_bytes()).decode("UTF-8").upper()

    def get_pubkey_point(self):
        vk = self.verifying_key
        return vk.pubkey.point

    def verify_bytes(self, msgbytes: bytes, sigbytes: bytes):
        """
        Verify that the message came from the address.
        """
        # first step is to make sure that the public key matches the address
        # Then, check the signature matches the message
        return self.verifying_key.verify(sigbytes, msgbytes)

    def verify_str(self, msg: str, sigbytes: bytes):
        return self.verify_bytes(bytes(msg, "UTF-8"), sigbytes)


class Account(ViewAccount):
    """
    Represents an account in the system.
    """
    def __init__(self, pk_wif: str=None, ecdsa_key: int=None):
        if pk_wif is None and ecdsa_key is None:
            self.signingkey = self.generate()
        elif pk_wif is not None:
            secret = int.from_bytes(base58.b58decode_check(pk_wif)[-32:], "big")
            self.signingkey = SigningKey.from_secret_exponent(secret, ecdsa.SECP256k1)
        else:
            self.signingkey = SigningKey.from_secret_exponent(ecdsa_key, ecdsa.SECP256k1)
        # Then, initialize the public key part
        super().__init__(self.signingkey.get_verifying_key().pubkey.point)

    @staticmethod
    def from_secret_str(secret: str):
        return Account(ecdsa_key=int.from_bytes(binascii.unhexlify(secret), "big"))

    @staticmethod
    def from_wif(pk_wif: str):
        return Account(pk_wif=pk_wif)

    @staticmethod
    def generate():
        return SigningKey.generate(curve=ecdsa.SECP256k1)

    def get_privkey_wif(self):
        raw_bytes = bytes.fromhex('80') + self.signingkey.privkey.secret_multiplier.to_bytes(32, "big")
        return base58.b58encode_check(raw_bytes)

    def get_secret(self):
        return self.signingkey.privkey.secret_multiplier

    def sign_str(self, msg: str):
        return self.sign_bytes(bytes(msg, "UTF-8"))

    def sign_bytes(self, msgbytes: bytes):
        return self.signingkey.sign(msgbytes)


def hash_point(p: ecdsa.ellipticcurve.Point):
    h = hashlib.sha256()
    h.update(p.x().to_bytes(32, "big"))
    h.update(p.y().to_bytes(32, "big"))
    return h.digest()


def point_to_addr(p: ecdsa.ellipticcurve.Point):
    return ViewAccount.from_pubkey_point(p).get_address()


class DepositTrans:
    def __init__(self, sender_addr: str, amount: int, S: ecdsa.ellipticcurve.Point, V: ecdsa.ellipticcurve.Point):
        self.amount = amount
        self.S = S
        self.V = V
        self.sender_addr = sender_addr

class Network:
    """
    Represents the network, which records the state and transactions.
    """
    def __init__(self):
        self.balances = dict()
        self.deposit_handlers = []

    def add_deposit_hook(self, hook):
        self.deposit_handlers.append(hook)

    def state_change(self, addr: str, amount: int, extradata: ecdsa.ellipticcurve.Point):
        self.balances[addr] = self.balances.get(addr, 0) + amount
        print("STATE_CHANGE:    bal : {} = {}. Extradata={}".format(addr, amount, point_to_addr(extradata)))

    def deposit(self, dt: DepositTrans):
        print("DEPOSIT     :    from: {} = {}. Extradata=(S:{},V:{})"
              .format(dt.sender_addr, dt.amount, point_to_addr(dt.S), point_to_addr(dt.V)))
        for h in self.deposit_handlers:
            h.handle_deposit(dt)

class Alice:
    """
    Alice is the sender
    """
    def __init__(self, network: Network):
        self.spend_key = Account()
        self.view_key = Account()
        self.network = network

    def set_peer(self, bob):
        self.bob = bob

    def get_spendkey_point(self):
        return self.spend_key.get_pubkey_point()

    def get_viewkey_point(self):
        return self.view_key.get_pubkey_point()

    def get_spend_account(self):
        return self.spend_key


class Bob:
    """
    Bob is the one doing the sending
    """
    def __init__(self, network: Network):
        self.spendkey = Account()
        self.network = network
        self.deposit_q = []

    def handle_deposit(self, dt: DepositTrans):
        """
        Clients will deposit some amount, and bob will turn it into the ERC token balance
        """
        r = int.from_bytes(hashlib.sha256(b"__some_random_number").digest(), "big")
        R = r * ecdsa.SECP256k1.generator

        rS = int.from_bytes(hash_point(r * dt.S), "big") % ecdsa.SECP256k1.generator.order() # Is this generator.order() or curve.p()?
        stealth_pubkey = rS * ecdsa.SECP256k1.generator + dt.V
        stealth_addr = ViewAccount.from_pubkey_point(stealth_pubkey).get_address()
        self.deposit_q.append((stealth_addr, +dt.amount, R))

        if len(self.deposit_q) == 3:
            for qi in self.deposit_q:
                self.network.state_change(addr=qi[0], amount=qi[1], extradata=qi[2])
            self.deposit_q = []

#     stealth_pubkey, R = b.deposit(1, a.get_spendkey_point(), a.get_viewkey_point())
#     sR = int.from_bytes(hash_point(a.spend_key.get_secret() * R), "big")
#     v = a.view_key.get_secret()
#     stealth_privkey = (sR + v) % ecdsa.SECP256k1.generator.order()
#
#     stealth_addr = ViewAccount.from_pubkey_point(stealth_pubkey).get_address()
#     steath_account = Account(ecdsa_key=stealth_privkey)
#
#     if stealth_addr != steath_account.get_address():
#         print("Stealth addr: {}".format(stealth_addr) \
#                + "Account addr: {}, Priv key: {}".format(steath_account.get_address(),
#                                                          steath_account.get_privkey_wif())


if __name__ == '__main__':
    n = Network()

    a1 = Alice(n)
    a2 = Alice(n)
    a3 = Alice(n)

    b = Bob(n)

    n.add_deposit_hook(b)

    n.deposit(DepositTrans(a1.get_spend_account().get_address(), 1, a1.get_spendkey_point(), a1.get_viewkey_point()))
    n.deposit(DepositTrans(a2.get_spend_account().get_address(), 2, a2.get_spendkey_point(), a2.get_viewkey_point()))
    n.deposit(DepositTrans(a3.get_spend_account().get_address(), 3, a3.get_spendkey_point(), a3.get_viewkey_point()))
