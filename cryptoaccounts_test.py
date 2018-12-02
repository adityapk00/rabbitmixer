import unittest
import ecdsa

from ecdsa.ellipticcurve import Point
from cryptoaccounts import Account, ViewAccount


class TestAccounts(unittest.TestCase):

    def test_account_accuracy(self):
        # known good tests for bitcoin address/privkey generation
        ecdsa_key       = "D7A197FE28D4C50C89D7E78FA9329EDCCC0F3480038ECF22B286EE5BD31F1FA1"
        wif_privkey     = "5KTFbGvR5hyWr5B2YQHbuXkLyyjKNLEwqKoyz1UTiC8X2SCWkY7"
        address         = "1DdN2AEzUVbxsZs76R6iuSGM689MdX7Gbh"
        pubkey          = "0490E7EF5B8986F2BA83A6C6728A3C9EA3A9AC91AEA64F77CE1CF0FE19B0178725CEC6C3E82EE1DD9DE9C795C3A2D164872A9EDED57FEA3A13AA26A7F8E90801E5"

        account = Account.from_secret_str(secret=ecdsa_key)
        self.assertEqual(account.get_privkey_wif(), wif_privkey)
        self.assertEqual(account.get_address(), address)
        self.assertEqual(account.get_pubkey(), pubkey)

        account = Account(pk_wif=wif_privkey)
        self.assertEqual(account.get_privkey_wif(), wif_privkey)
        self.assertEqual(account.get_address(), address)
        self.assertEqual(account.get_pubkey(), pubkey)

    def test_account(self):
        # Test accounts all line up
        acct1 = Account()
        acct2 = Account(acct1.get_privkey_wif())

        self.assertEqual(acct1.get_address(), acct2.get_address())

        # Test self signing
        acct = Account()
        msg = "This is a test message"
        sig = acct.sign_str(msg)
        self.assertTrue(acct.verify_str(msg, sig))

        # Test verification by a View Account only.
        acct_v = ViewAccount.from_pubkey(acct.get_pubkey())
        self.assertTrue(acct_v.verify_str(msg, sig))
        self.assertRaises(ecdsa.keys.BadSignatureError, lambda: acct1.verify_str(msg, sig))

    def test_hidden_amounts(self):
        abp = 6 # previous balance of A
        bbp = 2 # previous balance of B
        t = 5   # amount to transfer
        G = ecdsa.SECP256k1.generator

        T = t * G
        # nT is -T, so we can subtract T. In elliptic curve math, a - b is a + negation(b), where
        # negation(point) = (point.x, -point.y)
        nT = Point(ecdsa.SECP256k1.curve, T.x(), -T.y() % ecdsa.SECP256k1.curve.p())

        # These are the publicly visible previous balances of A and B
        Abp = abp * G
        Bbp = bbp * G

        # The smart contract will be able to calculate the new balances of A and B
        Abn = Abp + nT
        Bbn = Bbp + T

        # This is internal only. abn is the un-encrypted value of the new balance of A. Similarly,
        # bbn is the unencrypted value of the new balance of B
        abn = abp - t
        bbn = bbp + t

        # This is for testing purposes. These two should be equal to tG
        abG = (abp - abn) * G
        bbG = (bbn - bbp) * G
        self.assertEquals(abG, T)
        self.assertEquals(bbG, T)

        # These are internal calculations to verify the verifications for A
        abpG = abp * G
        abnG = abn * G
        self.assertEquals(abpG, Abp)
        self.assertEquals(abnG, Abn)

        # These are internal calculations to verify the verifications for B
        bbnG = bbn * G
        bbpG = bbp * G
        self.assertEquals(bbpG, Bbp)
        self.assertEquals(bbnG, Bbn)

        # TODO: How do we check:
        # that t > 0?
        # that abp > t?


if __name__ == '__main__':
    unittest.main()