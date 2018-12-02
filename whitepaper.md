
Alice (a, Pa). balance[Pa] = Ba (= bliding_a.G + ba.H)

Sending: t
Generate alpha (blinding factor)		# This also needs to be ephemeral key, and derive alpha from (eph * Pa), so that (eph.G) can be stored 
						# somewhere so that we can track the blinding keys on the blockchain itself. Maybe store the series of (eph.G) 
						# in an array, so that an account's blinding key can be recovered purely from the blockchain + private contract

Send T (= alpha.G + t.H)

Prove_positive T (= alpha.G + t.H)
Ba_new = Ba - (alpha.G + t.H)
Prove_positive  Ba_new (= Ba - (alpha.G + t.H))
			= blinding_a.G + ba.H - alpha.G - t.H 			# blinding_a_new = blinding_a - alpha
			= (blinding_a.G - alpha.G + ba.G - t.H)			# Note that if this is the first time Alice is sending a transaction, blinding_a = 0
			= (blinding_a - alpha).G + (ba - t).H			# so blinding_a_new = -alpha

Send to the Mixer:
	from: A to: B
	Publish T, S, Ba_new        # Where T = S + F
	Generata eph (Ephermeral key)
	derive beta = eph.B (Bob's Public key)		# Note that if Bob's never sent a transaction, then his public key is not available. So, we'll have to use
						# beta=0 (i.e., don't have a blinding factor)

Mixer calculates:
T 		= S + F
(alpha.G + t.H) = (beta.G + s.H) + ((alpha-beta).G + f.H)

Bb_new 				= Bb + S
((r+beta).G + (bb + s).H)     	= blinding_b.G + bb.H  + beta.G + s.H	


Blockchain Sees:
  Mixer says that Alice wants to send:
  	T amount
	range_proof of T>0
	Ba_new = Ba - T
	range_proof of Ba_new>0
	nonce				# To verify Mixer is not cheating by publishing an old/reused signed commitment. 
	Hash{
		B 			# The target address
		S 			# Pederson commitment of sending amount
		r			# Some random number
	}

  Mixer publishes
	Bb_new = Bb + S1
	(eph.G), ENC(msg=s,key=beta)				# Ephemeral Public key for ECDH, provided by Alice and an encrypted amount. If beta=0(no public key available for Bob), 
								# Then we don't provide these two values. 
	
	T_total =  T1 + T2 + T3.... 
	T_total = (Bb_new - Bb) + (X1b_new - X1b) + ...  	# Note, T_total includes fees. One of the X's is the fees collection address)
	T_total = S1 + S2 + .... + (F1 + F2 + F3)		# The fees are all added as one unit to one address		      


  Alice verifies
	Bb_new - Bb = S 	# Since Alice knows beta and s
	Bb_new - Bb = s.G + beta.H

	If it doesn't match:
	  Alice discloses B, (s.G + beta.H) (i.e., the target address and pederson commitment of sending amount)
	  Blockchain verifies:
		Hash(B || (s.G + beta.H)) matches the original commitment
		Bb_new - Bb = sG + beta.H

  Bob sees incoming transaction
  Bb_new = Bb + S
  derive beta = b.(eph.G)
  Bb_new = bb.G + blinding_b.H  + ?.G + beta.H		# How exactly does Bob identify the incoming amount? Even if beta and Bb_new is known

 ---------------
 Blinding factors:
 T = S + F
 (calc)t_blinding = (gen)s_blinding + (calc)f_blinding
 
 (gen)new_bal_blinding = (existing)bal_blinding - (calc)t_blinding
 
 Sender to log:
   - encrypted(t) # So that we can know the amount that was spent. 
   - blinding_factor is generate from the sha3(privatekey || nonce)
   
 Reciever gets:
   - eph_pub # ephemeral public key to reconstruct the shared secret
   - encrypted(s) # so that the reciever knows how much he got. 
   - blinding_factor is generated from shared secret 
   
   
--------------------
Test data
//transaction_publish_sender_proof
"0x29d8d46307144b8853ff0282d106a8760a1b93cc", 
1, 
"351607967545289426383283781951695935915433726869953709408040536710339112781", "11862849145115301393572481168328416234249685512505662034573626725144992050194",
0,
"0x6269e027b9605f19adef05a503fa47f4bc279b048fe55fa935d3bcd5f99b3ea4", 
"0x1c", "0x36504847400deb8ef71a7d198abc25734dfcfcf2007a868aa1ece4ee8c3a83f0", "0x694886521f4a433b016ada508f336135014d88d79844f98a063190425155abc5"

// transaction_publish_reciever_proof
1,
["0x8a750de04cf77eaeeb58f3631820a8ac52a9e790", "0x8a750de04cf77eaeeb58f3631820a8ac52a9e790"],
["60460010703867068782268232543215564804665481040863564224314590519251018444968", "5742946746831988922780203318051412492843241882443824782923333380121435753551"]

F["5742946746831988922780203318051412492843241882443824782923333380121435753551", "6204641297128831320038834081327386630596332732276822536510174207082504299878"]
S["2563966085208971070482740038871610878030488708043282204585798515294453625000", "4513894734672856753799101918530619943087832070690071729218835461829540121875"]
T["351607967545289426383283781951695935915433726869953709408040536710339112781", "11862849145115301393572481168328416234249685512505662034573626725144992050194"]