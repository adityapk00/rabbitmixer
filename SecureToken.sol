pragma solidity ^0.4.20;

contract ECMath {
	//alt_bn128 constants
	uint256[2] internal G1;
	uint256[2] internal H;
	uint256 constant internal NCurve = 0x30644e72e131a029b85045b68181585d2833e84879b9709143e1f593f0000001;
	uint256 constant internal PCurve = 0x30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd47;

	//Used for Point Compression/Decompression
	uint256 constant internal ECSignMask = 0x8000000000000000000000000000000000000000000000000000000000000000;
	uint256 constant internal a = 0xc19139cb84c680a6e14116da060561765e05aa45a1c72a34f082305b61f3f52; // (p+1)/4

	function ECMath() public {
        G1[0] = 1;
    	G1[1] = 2;
    	H[0] = 17856212038068422348937662473302114032147350344021172871924595963388108456668;
    	H[1] = 21295818415838735026194046494954432012836335667085206402831343127503290780315;
	}
	
	//Base EC Functions
	function ecAdd(uint256[2] p0, uint256[2] p1)
    	internal constant returns (uint256[2] p2)
	{
    	assembly {
        	//Get Free Memory Pointer
        	let p := mload(0x40)

        	//Store Data for ECAdd Call
        	mstore(p, mload(p0))
        	mstore(add(p, 0x20), mload(add(p0, 0x20)))
        	mstore(add(p, 0x40), mload(p1))
        	mstore(add(p, 0x60), mload(add(p1, 0x20)))

        	//Call ECAdd
        	let success := call(sub(gas, 2000), 0x06, 0, p, 0x80, p, 0x40)

        	// Use "invalid" to make gas estimation work
         	switch success case 0 { revert(p, 0x80) }

         	//Store Return Data
         	mstore(p2, mload(p))
         	mstore(add(p2, 0x20), mload(add(p,0x20)))
    	}
	}

	function ecMul(uint256[2] p0, uint256 s)
    	 internal constant returns (uint256[2] p1)
	{
    	assembly {
        	//Get Free Memory Pointer
        	let p := mload(0x40)

        	//Store Data for ECMul Call
        	mstore(p, mload(p0))
        	mstore(add(p, 0x20), mload(add(p0, 0x20)))
        	mstore(add(p, 0x40), s)

        	//Call ECAdd
        	let success := call(sub(gas, 2000), 0x07, 0, p, 0x60, p, 0x40)

        	// Use "invalid" to make gas estimation work
         	switch success case 0 { revert(p, 0x80) }

         	//Store Return Data
         	mstore(p1, mload(p))
         	mstore(add(p1, 0x20), mload(add(p,0x20)))
    	}
	}

	function CompressPoint(uint256[2] Pin)
    	internal pure returns (uint256 Pout)
	{
    	//Store x value
    	Pout = Pin[0];

    	//Determine Sign
    	if ((Pin[1] & 0x1) == 0x1) {
        	Pout |= ECSignMask;
    	}
	}

	function EvaluateCurve(uint256 x)
    	internal constant returns (uint256 y, bool onCurve)
	{
    	uint256 y_squared = mulmod(x,x, PCurve);
    	y_squared = mulmod(y_squared, x, PCurve);
    	y_squared = addmod(y_squared, 3, PCurve);

    	uint256 p_local = PCurve;
    	uint256 a_local = a;

    	assembly {
        	//Get Free Memory Pointer
        	let p := mload(0x40)

        	//Store Data for Big Int Mod Exp Call
        	mstore(p, 0x20)             	//Length of Base
        	mstore(add(p, 0x20), 0x20)  	//Length of Exponent
        	mstore(add(p, 0x40), 0x20)  	//Length of Modulus
        	mstore(add(p, 0x60), y_squared) //Base
        	mstore(add(p, 0x80), a_local)   //Exponent
        	mstore(add(p, 0xA0), p_local)   //Modulus

        	//Call Big Int Mod Exp
        	let success := call(sub(gas, 2000), 0x05, 0, p, 0xC0, p, 0x20)

        	// Use "invalid" to make gas estimation work
         	switch success case 0 { revert(p, 0xC0) }

         	//Store Return Data
         	y := mload(p)
    	}

    	//Check Answer
    	onCurve = (y_squared == mulmod(y, y, PCurve));
	}

	function ExpandPoint(uint256 Pin)
    	internal constant returns (uint256[2] Pout)
	{
    	//Get x value (mask out sign bit)
    	Pout[0] = Pin & (~ECSignMask);

    	//Get y value
    	bool onCurve;
    	uint256 y;
    	(y, onCurve) = EvaluateCurve(Pout[0]);

    	//TODO: Find better failure case for point not on curve
    	if (!onCurve) {
        	Pout[0] = 0;
        	Pout[1] = 0;
    	}
    	else {
        	//Use Positive Y
        	if ((Pin & ECSignMask) != 0) {
            	if ((y & 0x1) == 0x1) {
                	Pout[1] = y;
            	} else {
                	Pout[1] = PCurve - y;
            	}
        	}
        	//Use Negative Y
        	else {
            	if ((y & 0x1) == 0x1) {
                	Pout[1] = PCurve - y;
            	} else {
                	Pout[1] = y;
            	}
        	}
    	}
	}

    //Return H = keccak256(p)
    function HashOfPoint(uint256[2] point)
        internal pure returns (uint256 h)
    {
        return uint256(keccak256(point[0], point[1])) % NCurve;
    }


    //Ring Signature Functions
    function CalculateRingSegment_NoHash(uint256 ck, uint256[2] Pk, uint256 sk)
        internal constant returns (uint256[2] Pout)
    {
        uint256[2] memory temp1;
        temp1 = ecMul(Pk, ck);
        Pout = ecMul(G1, sk);
        Pout = ecAdd(temp1, Pout);
    }

    function CalculateRingSegment(uint256 ck, uint256[2] Pk, uint256 sk)
        internal constant returns (uint256 ckp)
    {
        ckp = HashOfPoint(CalculateRingSegment_NoHash(ck, Pk, sk));
    }

    //CompleteRing = (alpha - c*xk) % N
    function CompleteRing(uint256 alpha, uint256 c, uint256 xk)
        internal pure returns (uint256 s)
    {
        s = mulmod(c, xk, NCurve);
        s = NCurve - s;
        s = addmod(alpha, s, NCurve);
    }
}

contract RingCT is ECMath {
    //function: CTVerifyTx
    //  description: Verifies a Pedersen Commitment set and Range Proof
    //  notes:
    //      N   - the number of "base4 bits" in the commitment
    //            e.g. 19 can be represented by 3 base4 bits: 3*(4^0) + 0*(4^1) + 1*(4^2)
    //  inputs:
    //      ip (uint256[])      - transaction data ready to be processed by the contract
    //          ip[0]           - power of 10 to be added to committed value (publicly known)
    //          ip[1]           - offset to be added to committed value (publicly known)
    //          ip[2]           - total pedersen commitment (compresed EC Point)
    //          ip[3 ... N+1]   - base4 bit pedersen commitments (minus the last one which is implied, total of N-1 compressed EC Points)
    //          ip[N+2]         - c0 for borromean ring signatures (start of range proof)
    //          ip[N+3 ... 5N+2]- sk values for borromean ring signatures (4 for each base4 bit)
    //  outputs:
    //      result (uint256)    - result of verification, different codes mean different things
    function CTVerifyTx(uint256[] ip)
        public constant returns (bool success)
    {
        //Check for proper number of input parameters (5N+3), must be at least one bit
        require( (ip.length >= 8) );
        require( (ip.length - 3) % 5 == 0 );
        uint256 N = (ip.length - 3) / 5 ;

        //Expand Pedersen Commitments
        uint256 i;
        uint256[2] memory temp1;
        uint256[2] memory temp2;
        uint256[] memory PC = new uint256[](2*N+2);

        //Expand Total Pedersen Commitment
        temp2 = ExpandPoint(ip[2]);
        // If point is not on curve, return false.
        if (temp2[0] == 0 && temp2[1] == 0) {
            return false;
        }


        //Store Total Pedersen Commitment
        (PC[2*N], PC[2*N+1]) = (temp2[0], temp2[1]);

        //Expand Stored Bitwise Pedersen Commitments and generate last one
        for (i = 0; i < (N-1); i++) {
            //Expand Stored Point
            temp1 = ExpandPoint(ip[i+3]);

            // If point is not on curve, return false.
            if (temp1[0] == 0 && temp1[1] == 0) {
                return false;
            }

            //Store Bitwise Pedersen Commitment
            (PC[2*i], PC[2*i+1]) = (temp1[0], temp1[1]);

            //Negate Bitwise Pedersen Commitment
            temp1[1] = PCurve - temp1[1];

            //Add Bitwise Pedersen Commitment to total
            temp2 = ecAdd(temp2, temp1);
        }

        //Subtract offset*H
        if (ip[1] > 0) {
            temp1 = ecMul(H, ip[1]);
            temp1[1] = PCurve - temp1[1];
            temp2 = ecAdd(temp1, temp2);
        }

        //Store Final Bitwise Pedersen Commitment
        (PC[2*N-2], PC[2*N-1]) = (temp2[0], temp2[1]);

        //Verify Range Proof
        {
            //Memory for uncompressed pedersen counter commitments
            uint256[] memory PCp = new uint256[](6*N);
            uint256[] memory c_points = new uint256[](2*N);

            for (i = 0; i < N; i++) {
                //Calculate counter Pedersen Comitments
                temp1 = ecMul( H, (4**i)*(10**ip[0]) );
                temp1[1] = PCurve - temp1[1];   //Negate EC Point (use other y-value)

                (temp2[0], temp2[1]) = (PC[2*i], PC[2*i+1]);

                temp2 = ecAdd(temp1, temp2);    //  PC' = PC - (4^i)*(10^power)*H
                (PCp[6*i], PCp[6*i+1]) = (temp2[0], temp2[1]);

                temp2 = ecAdd(temp1, temp2);    // PC'' = PC' - (4^i)*(10^power)*H
                (PCp[6*i+2], PCp[6*i+3]) = (temp2[0], temp2[1]);

                temp2 = ecAdd(temp1, temp2);    //PC''' = PC'' - (4^i)*(10^power)*H
                (PCp[6*i+4], PCp[6*i+5]) = (temp2[0], temp2[1]);

                //Calculate c1 = HashOfPoint(c0*PC+s0*G)
    	        temp1[0] = CalculateRingSegment(ip[N+2], [PC[2*i], PC[2*i+1]], ip[4*i+N+3]);

    	        //Calculate c2 = HashOfPoint(c1*PC'+s1*G)
    	        temp1[0] = CalculateRingSegment(temp1[0], [PCp[6*i], PCp[6*i+1]], ip[4*i+N+4]);

    	        //Calculate c3 = HashOfPoint(c2*PC''+s2*G)
    	        temp1[0] = CalculateRingSegment(temp1[0],  [PCp[6*i+2], PCp[6*i+3]], ip[4*i+N+5]);

    	        //Calculate input point for c0 = c3*PC'''+ s3G
        	    temp1 = CalculateRingSegment_NoHash(temp1[0],  [PCp[6*i+4], PCp[6*i+5]], ip[4*i+N+6]);
        	    (c_points[2*i], c_points[2*i+1]) = (temp1[0], temp1[1]);
            }

            //Construct c0 (store in out[N+2])
        	//  = keccak256 of either c1*PC'+s1*G (x for PC known) or alpha*G (x for PC' known) from each ring
        	//  = keccak256(c1*PC'+s1*G, alpha*G, alpha*G, ... etc)
        	assembly {
        	    let p := mload(0x40)
        	    mstore(p, mul(mul(N, 2), 0x20))
        	    mstore(temp1, keccak256(c_points, mload(p)))
        	}

            //Check that original c0 matchs the new one (ring is closed)
            if (temp1[0] == ip[N+2]) {
                success = true;
            }
            else {
                success = false;
            }
        }
    }
}

contract SecureToken is RingCT {
    event BRMDepositTokenCompleteEvent(address addr, uint256 amount);
    event BRMRecieverTransactionCompleteEvent(address addr, uint256 amount, uint256 eph_pub);
    event BRMSenderTransactionCompleteEvent(address addr, uint256 new_balance_enc);

    /** The owner that created the contract */
    address owner;
    
    /** The mixer is the Ether address allowed to execute mix transactions. 
     * This is the address that puts up the bounty.
     */ 
    address mixer;

    /**
     * Modifier to allow some functions to be only called by the owner of the contract
     */ 
    modifier onlyOwner() {
        require(msg.sender == owner); 
        _;                              // Otherwise, it continues.
    } 

    /**
     * Modifier to allow some functions to be only called by the mixer
     */ 
    modifier onlyMixer() {
        require(msg.sender == mixer); 
        _;                              // Otherwise, it continues.
    } 
    
    /**
     * Amount of bounty available to anyone that successfully challenges a mix. 
     * The entire bounty is paid out, and the mixer must re-put up the bounty_amount
     * if it is ever claimed.
     */ 
    uint256 public bounty_amount;
    
    /**
     * Contructor, needs to send in the bounty amount of at least 10 ether. Here,
     * we default the mixer to be the same as the creator of the contract. 
     */ 
    function SecureToken() public payable {
        owner = msg.sender;
        mixer = msg.sender;
        
        require(msg.value >= 10 ether);
        
        bounty_amount = msg.value;
    }
    
    /**
     * Reset the mixer and re put up the bounty. This is to be called in the
     * event that someone successfully challenges the bounty, and the contract
     * has to be reset to a new mixer. Can only be allowed by the owner.
     * 
     * If there is any pending bounty, it is sent back to the current mixer. 
     */ 
    function bounty_reset(address addr) 
    public payable 
    onlyOwner()
    {
        require(bounty_amount == 0);
        require(mixer == 0);
        
        require(msg.value >= 10 ether);
        
        // Return the existing bounty back to the current mixer. 
        if (bounty_amount > 0) {
            mixer.transfer(bounty_amount);
        }
        
        mixer = addr;
        bounty_amount = msg.value;
    }


    /** 
     * Struct to hold the account info. 
     */
    struct Balances {
        uint256 balance;    // Stealth Balance, Compressed bn_128 point = blinding.G + balance.H
        uint128 nonce;      // Stealth Transaction nonce, to prevent unauthorized spending
        bool    locked;     // If a Stealth transfer from this address is in progress
    }

    /** 
     * Account balances. The balance is a compressed public point, representing
     * a.G + b.H
     * Where b is the balance and a is the pederson blinding factor.
     */
    mapping(address => Balances) public balances;

    /** The total amount of tokens hidden in the balances. This is also used to audit
     * the contract (See auditing section in the white paper) */
    uint256 public total_token_supply;

    /**
     * Deposit some ether into the contract and turn it into stealth ethers. Note that the
     * deposit method doesn't sheild the amount, since anyone can see how much ether this method was sent. 
     * It also creates the stealth ethers with no blinding factor, so they are not really sheilded
     * until they are sent to another stealth address.
     * 
     * CALLABLE BY: everyone
     */ 
    function deposit() 
    public 
    payable {
        require(msg.value > 0);
        
        uint256[2] memory commited_amount = ecMul(H, msg.value);
        
        if (balances[msg.sender].balance != 0) {
            uint256[2] memory existing = ExpandPoint(balances[msg.sender].balance);
            commited_amount = ecAdd(existing, commited_amount);
        }
        
        balances[msg.sender].balance = CompressPoint(commited_amount);
        total_token_supply += msg.value;
        emit BRMDepositTokenCompleteEvent(msg.sender, msg.value);
    }

    

    /** BEGIN DEVELOPMENT/TESTNET ONLY **/
    function deleteContract() public {
        require(msg.sender == owner);
        selfdestruct(msg.sender);
    }
    /** END DEVELOPMENT/TESTNET ONLY **/


    // Up to 5 mixer transactions can be present at a time, and each mix needs 3 range proofs (t, new_balance)
    // per sender, and can have upto 10 senders
    // So, the location of ith mix's jth senders' kth range proof is (i * (2*10) + j*2 + k)
    uint256[][10 * 2 * 5] sender_range_proof_caches;
    
    // Up to 5 mixer transactions can be present at a time, and each mix needs 1 range proof per reciever, can have upto 11 recievers per mix
    uint256[][11 * 1 * 5] reciever_range_proof_caches;

    /** Struct that holds the sender in a given mix */
    struct SenderInfo {
        address addr;           // Source address of the sender
        uint256 total_amount;   // pederson commitment of the total amount sent (including fees)
        bytes32 reciever_hash;  // Secret reciever info hash, which is used to challenge if the mixer is up to no good. 
    }
    
    /** Struct that holds the reciever's info in a given mix */
    struct RecieverInfo {
        address addr;                       // Reciever address
        uint256 total_stealth_amount;       // pederson commitment of the amount the reciever gets
        uint256 total_transparent_amount;   // If this is a withdrawal transaction, this field is set and the total_stealth_amount is 0. 
    }

    /** State of the current mix **/
    struct MixState {
        uint256 status; // 1 = preparing, 2 = prepared, 3 = confirmed, 4 = executed, 5 = cancelled
        uint256 expiry;

        SenderInfo[] senders;
        RecieverInfo[] recievers;
    }

    // Mapping from Mix ID to the details of the mix transaction. There can 
    // be at most 10 mix transactions in parallel. 
    MixState[10] public mix_details; 
    
    /** Read the mix transanction status */
    function transaction_get_status(uint256 mix_number) 
    view 
    public 
    returns (uint256)
    {
        return mix_details[mix_number].status;
    }

    /** 
     * Initialize a mix. There isn't anything to do here, except update the status. Most of the mix's work 
     * is done in subsequent functions. 
     * 
     * CALLABLE BY: Mixer only
     */ 
    function transaction_start_preparing(uint256 mix_number) 
    onlyMixer() 
    public {
        // Make sure that the existing transaction at the slot it either executed or cancelled or blank
        require(mix_details[mix_number].status == 0);

        mix_details[mix_number].status = 1;
        
        // TODO: Make sure expiry works
        mix_details[mix_number].expiry = now + 1 days;
    }

    /**
     * Anyone can call this method to clean up a mix that has expired. This can happen if the mixer is down
     * or some eventuality like that. Shouldn't happen on a regular basis, hopefully. 
     * 
     * CALLABLE BY: everyone
     */ 
    function challenge_transaction_expiry(uint256 mix_number) 
    public {
        // If a transaction has expired, then mark it cancelled, since it was not removed cleanly
        // Also, we'll give a grace period of a day for the owner of the transaction to clean it up, since the owner has to pay
        // a bounty if someone else cleans up a transaction.
        if(mix_details[mix_number].expiry > now) {
            // This mix has expired, and should be canclled
            mix_details[mix_number].status = 5; // cancelled
            
            // TODO: How much bounty should we pay?
            mix_cancel(mix_number);
        }
    }

    /**
     * Publish a sender's range proof. Each sender has to submit 3 range proofs. One each for:
     * T            - the transaction amount
     * new balance  - which is equal to old balance - T
     * 
     * S            - the amount the reciever is recieving. This is T - mixer fee
     * 
     * Range proofs for T and new balance are published here (as proof numbers 0 and 1 respectively), while the range proof for S 
     * is published as a part of the reciever functions. 
     * 
     * CALLABLE BY: Mixer only
     */ 
    function transaction_publish_sender_range_proof
        (uint256 mix_number, uint256 sender_number, uint256 range_proof_number, 
         uint256[] proof) 
    onlyMixer()
    public 
    {
        require(range_proof_number < 2);
        require(sender_number < 10);
        require(mix_details[mix_number].status == 1);

        // We will require the offset to be <1000 Ether
        require(proof[1] < 1000 ether);

        
        // We will also check that the proof is really for what the sender 
        // published
        if (range_proof_number == 0) { 
            // T
            require(mix_details[mix_number].senders[sender_number].total_amount == proof[2]);
        } else { 
            // New Balance
            // TODO: There's an edge case here where this might fail, if the sender has recieved a stealth transfer after they submitted the
            // new balance range proof, but before this function was called. 
            SenderInfo storage sender =  mix_details[mix_number].senders[sender_number];
            uint256[2] memory negative_t = ExpandPoint(sender.total_amount);
            negative_t[1] = PCurve - negative_t[1];
            uint256[2] memory new_balance = ecAdd(ExpandPoint(balances[sender.addr].balance), negative_t);
            require(CompressPoint(new_balance) == proof[2]);
        }
        sender_range_proof_caches[(mix_number*10*2) + (sender_number*2) + range_proof_number] = proof;
    }

    /**
     * Publish the signature of the sender, verifying onchain that we have authorization from the sender to mix a transaction 
     * worth the given amount. 
     * 
     * CALLABLE BY: Mixer only
     */ 
    function transaction_publish_sender_proof(
            address from_addr,
            uint256 mix_number,
            uint256 total_amount_commitment_x,
            uint256 total_amount_commitment_y,
            uint256 nonce,
            bytes32 reciever_hash,
            uint8 v, bytes32 r, bytes32 s) 
    onlyMixer() 
    public {
        require(mix_details[mix_number].status == 1);
        require(balances[from_addr].locked == false);

        // Validate nonce
        require(balances[from_addr].nonce == nonce);

        // Validate the signature
        require(ecrecover( 
                        keccak256("\u0019Ethereum Signed Message:\n32", 
                            keccak256(from_addr, nonce, total_amount_commitment_x, total_amount_commitment_y, reciever_hash)),
                        v, r, s) == from_addr);

        
        // Then, add it to the transaction
        require(mix_details[mix_number].senders.length < 6);

        // If all the verifications pass, then store this sender's info into the mix details. 
        uint256[2] memory temp;
        temp[0] = total_amount_commitment_x;
        temp[1] = total_amount_commitment_y;
        mix_details[mix_number].senders.push(SenderInfo(from_addr, CompressPoint(temp), reciever_hash));
        balances[from_addr].locked = true;
    }
    
    
    /**
     * Challenge a sender's range proof. If this challenge succeeds, the caller will be paid a bounty!
     * 
     * CALLABLE BY: Everyone
     */ 
    function challenge_sender_proof(uint256 mix_number, uint256 sender_number, uint256 proof_number) 
    public {
        uint256 mixstatus = mix_details[mix_number].status;
        // Mix has to be in the reciever published state or verified state
        //require(mixstatus == 2 || mixstatus == 3); 
       
        if (verify_sender_proof(mix_number, sender_number, proof_number) == false) {
            // OMG! Pay the bounty
            mix_cleanup(mix_number);
            pay_bounty();
        }
    }
    
    /**
     * Verify the range proofs for a sender's mix. This function is constant, so it can be called on your 
     * own node. If this function fails to verify a sender's range proof, there's an oppurtunity
     * to call the corresponding challenge_ method to claim a bounty!
     */ 
    function verify_sender_proof(uint256 mix_number, uint256 sender_number, uint256 proof_number) public constant returns (bool) {
        // TODO: Verify mix_number and all the other arguments
        
        return CTVerifyTx(sender_range_proof_caches[(mix_number*10*2) + (sender_number*2) + proof_number]); 
    }
    

    /**
     * Publish the range proof for reciever's amounts. This can be challenged via challenge_reciever_proof() if the mixer
     * is cheating.
     * 
     * CALLABLE BY: Mixer Only
     */ 
    function transaction_publish_reciever_range_proof(uint256 mix_number, uint256 receiver_number, uint256[] proof) 
    onlyMixer()
    public {
        require(receiver_number < 11);
        require(mix_details[mix_number].status == 1);

        reciever_range_proof_caches[(mix_number*11) + receiver_number] = proof;
    }

    /**
     * Recievers can be stealth of transparent. reciever_amount_commitment contains the pederson commitment of the amount
     * and if a reciever is transparent reciever_transparent_amount contains the transparent amount. The sum of the two arrays
     * should equal the number of recievers.
     * 
     * CALLABLE BY: Mixer Only
     * 
     * TODO: We can't pass empty arrays into solidity? To get around, temporarily pass a useless "0" as the first transparent amount.
     */ 
    function transaction_publish_reciever_proof
        (uint256 mix_number, address[] recievers, uint256[] reciever_amount_commitments, uint256[] reciever_transparent_amounts)
    onlyMixer()
    public
    {
        // Do the transaction
        require(mix_details[mix_number].status == 1);
        require(mix_details[mix_number].senders.length > 0);
        require(recievers.length < 20);
        require(recievers.length == reciever_amount_commitments.length + reciever_transparent_amounts.length - 1);

        uint256 i;
        // Store the new reciever balances. First for all the pederson commitments
        for(i=0; i < reciever_amount_commitments.length; i++) {
            mix_details[mix_number].recievers.push(RecieverInfo(recievers[i], reciever_amount_commitments[i], 0));
        }
        
        uint256 prev = reciever_amount_commitments.length;
        
        // Next for the transparent amounts. Note the TODO at the top, the first argument is a useless 0
        for(i=1; i < reciever_transparent_amounts.length; i++) {
            mix_details[mix_number].recievers.push(RecieverInfo(recievers[prev+(i-1)], 0, reciever_transparent_amounts[i]));
        }
        
        // Make it prepared
        mix_details[mix_number].status = 2;
    }
    
    
    /**
     * Challenge the mixer to see if the mixer is sending a sender's transaction to the intended destination. 
     * If this transaction succeeds, then a bounty is paid to the caller. Note that this can be called by anyone, 
     * not just the sender, but they need to know the randomness that was included in the sender's reciever_hash
     */ 
    function challenge_senders_reciever(uint256 mix_number, uint256 sender_number, uint256 randomness) public {
        uint256 mixstatus = mix_details[mix_number].status;
        // Mix has to be in the reciever published state or verified state
        require(mixstatus == 2 || mixstatus == 3); 
        
        if (verify_senders_reciever(mix_number, sender_number, randomness) == false) {
            // OMG! Pay the bounty
            mix_cleanup(mix_number);
            pay_bounty();
        }
    }
    
    
    /**
     * Verify that there a reciever that matches the sender's reciever hash. This is to ensure that the mixer can't cheat by sending the 
     * sheilded ether to a different address than the sender intended. 
     */ 
    function verify_senders_reciever(uint256 mix_number, uint256 sender_number, uint256 randomness) 
    public 
    view
    returns (bool) {
        SenderInfo storage s = mix_details[mix_number].senders[sender_number];
        
        uint256[2] memory S;
        
        uint256 i;
        for(i=0; i < mix_details[mix_number].recievers.length; i++) {
            RecieverInfo storage r = mix_details[mix_number].recievers[i];
            // CASE 1: For sheilded transfers
            if (r.total_transparent_amount == 0) {
                S = ExpandPoint(r.total_stealth_amount);
            
                // Calculate the reciever hash
                bytes32 hash = keccak256(s.addr, S[0], S[1], randomness);
                if (hash == s.reciever_hash) return true;
            }
        }
        
        // No Reciever matched the sender's reciever_hash, so the mixer is maybe cheating?
        return false;
    }
    
    
    /**
     * TODO: This function needs to be called by a verifier.
     */ 
    function transaction_verify(uint256 mix_number) 
    public {
        // TODO: This should be done by a validator or set to 2 after the confirmation time has expired
        mix_details[mix_number].status = 3;
    }
    
    
    /**
     * Challenge the integrity of the mix amounts. If this challenge succeeds, then the caller 
     * is rewarded with a bounty.
     * 
     * CALLABLE BY: Everyone
     */ 
    function challenge_mix_amounts(uint256 mix_number) 
    public {
        uint256 mixstatus = mix_details[mix_number].status;
        // Mix has to be in the reciever published state or verified state
        require(mixstatus == 2 || mixstatus == 3); 
       
        if (verify_mix_amounts(mix_number) == false) {
            // OMG! Pay the bounty
            mix_cleanup(mix_number);
            pay_bounty();
        }
    }
    
    /**
     * Fucntion to verify the mix amounts that are part of this transaction. This is a constant
     * function, so you can run it on your own node to check if the mixer is cheating. If this function
     * returns false, there is an oppurtunity to challenge this mix via the corresponding challenge_ function
     * and claim a bounty!
     */ 
    function verify_mix_amounts(uint256 mix_number) 
    public 
    view
    returns (bool) {
        require(mix_details[mix_number].senders.length > 0);
        
        uint256[2] memory T_Total;
        T_Total = ExpandPoint(mix_details[mix_number].senders[0].total_amount);
        // Start at 1, since we already included [0] above
        uint256 i;
        for (i=1; i < mix_details[mix_number].senders.length; i++) {
            T_Total = ecAdd(T_Total, ExpandPoint(mix_details[mix_number].senders[i].total_amount));
        }
        
        // Calculating total reciever amounts has to add up the stealth and transparent amounts
        uint256[2] memory R_total;
        for (i=0; i < mix_details[mix_number].recievers.length; i++) {
            if (mix_details[mix_number].recievers[i].total_stealth_amount == 0) {
                // Transparent balance, add the transparent_amount.H (blinding factor is 0, so the G term is 0)
                R_total = ecAdd(R_total, ecMul(H, mix_details[mix_number].recievers[i].total_transparent_amount));
            } else {
                // Stealth amount, add the stealth amount directly, since it is already a pederson commitment. 
                R_total = ecAdd(R_total, ExpandPoint(mix_details[mix_number].recievers[i].total_stealth_amount));
            }
        }
        
        
        return T_Total[0] == R_total[0] && T_Total[1] == R_total[1];
    }
    
    
    /**
     * Challenge a reciever's range proof. If this challenge succeeds, then the caller
     * will be rewarded with a bounty.
     * 
     * CALLABLE BY: Everyone
     */ 
    function challenge_reciever_proof(uint256 mix_number, uint256 reciever_number) 
    public {
        uint256 mixstatus = mix_details[mix_number].status;
        // Mix has to be in the reciever published state or verified state
        require(mixstatus == 2 || mixstatus == 3); 
       
        if (verify_reciever_proof(mix_number, reciever_number) == false) {
            // OMG! Pay the bounty
            mix_cleanup(mix_number);
            pay_bounty();
        }
    }
    
    /**
     * Verifies that the reciever's range proofs are all valid. This function can be called on your own node 
     * (so no need to pay gas) to verify that the mixer is doing stuff right. 
     * 
     * If this function returns false, there is an oppurtunity to claim a bounty by calling the corresponding
     * challenge_ function.
     */ 
    function verify_reciever_proof(uint256 mix_number, uint256 reciever_number) 
    public 
    view
    returns (bool) {
        // Verify mix number and reciever number
        
        // First, verify that the range proof is really for S
        if (mix_details[mix_number].recievers[reciever_number].total_stealth_amount == 0) {
            // Then there is nothing to verify for the range proof, since the balance is transparent. Just return
            return true;
        }
        
        // TODO: Handle offsets here as well
        uint256[] storage proof = reciever_range_proof_caches[(mix_number*11) + reciever_number];
            
        uint256 proof_value = proof[2];
        uint256[2] memory offset_value = ecAdd(ExpandPoint(proof_value), ecMul(H, proof[1]));
        bool verified = mix_details[mix_number].recievers[reciever_number].total_stealth_amount == CompressPoint(offset_value);
        if (!verified) return false;
        
        return CTVerifyTx(reciever_range_proof_caches[(mix_number*11) + reciever_number]);
    }
    
    /**
     * Execute the transaction. 
     * 
     * Note that if reciever_pub is 0, then the reciever amount is a transparent amount, to be interpreted literally (not as a point)
     * 
     * CALLABLE BY: Mixer only
     */
    function transaction_execute(uint256 mix_number, uint256[] sender_datas, uint256[] reciever_amounts, uint256[] reciever_pubs) 
    onlyMixer()
    public {
        require(mix_details[mix_number].status == 3);
        
        require(sender_datas.length == mix_details[mix_number].senders.length);
        require(reciever_amounts.length == reciever_pubs.length);
        // require(reciever_pubs.length == mix_details[mix_number].recievers.length); - We need to count only the stealth recievers here. 
        
        // TODO: What checks to we need here?
        
        uint256 i;
        
        // Update the sender's balances first. This is because if the an address is both the sender and a reciever in a 
        // transaction, we need to ensure that we deduct the amount incoming first, before adding it.
        for(i=0; i < mix_details[mix_number].senders.length; i++) {
            SenderInfo memory sender             = mix_details[mix_number].senders[i];
            uint256[2] memory total_amount       = ExpandPoint(sender.total_amount);
            total_amount[1]                      = PCurve - total_amount[1];
            uint256[2] memory new_balance_amount = ecAdd(ExpandPoint(balances[sender.addr].balance), total_amount);
            balances[sender.addr].balance        = CompressPoint(new_balance_amount);
            balances[sender.addr].nonce          = balances[sender.addr].nonce + 1;
            emit BRMSenderTransactionCompleteEvent(sender.addr, sender_datas[i]);
        }
        
        // Update the reciever's balances to new balances and the sender's new balances as well
        for(i=0; i<mix_details[mix_number].recievers.length; i++) {
            RecieverInfo memory reciever = mix_details[mix_number].recievers[i];
            if (reciever.total_stealth_amount == 0) {
                // This is a withdraw transaction, so publicly withdraw and send regular ether. 
                reciever.addr.transfer(reciever.total_transparent_amount);
                total_token_supply -= reciever.total_transparent_amount;
            } else {
                balances[reciever.addr].balance 
                    = CompressPoint(ecAdd(
                                    ExpandPoint(balances[reciever.addr].balance), 
                                    ExpandPoint(reciever.total_stealth_amount)));
                emit BRMRecieverTransactionCompleteEvent(reciever.addr, reciever_amounts[i], reciever_pubs[i]);
            }
        }
        
        mix_details[mix_number].status = 4;

        mix_cleanup(mix_number);
    }
    
    /**
     * Withdraw all funds by disclosing both the balance and blinding. This is used by an end user to withdraw without
     * going through the mixer. 
     */ 
    function withdraw_all(uint256 balance, uint256 blinding) 
    public
    {
        uint256 calc_balance = CompressPoint(ecAdd(ecMul(G1, blinding), ecMul(H, balance)));
        require(balances[msg.sender].balance == calc_balance);
        
        if (balance > 0) {
            msg.sender.transfer(balance);
            total_token_supply -= balance;
        }
        
        delete balances[msg.sender];
    }
    
    function pay_bounty() 
    internal 
    {
        msg.sender.transfer(bounty_amount);
        bounty_amount = 0;
        mixer = 0;
    }
    
    /**
     * Cancels a mix that is in progress. This should not happen regularly, and is only
     * present to roll back a transaction if the mixer publishes a bad proof of somthing 
     * (Which hopefully never happens)
     * 
     * CALLABLE BY: Mixer Only
     */ 
    function mix_cancel(uint256 mix_number) 
    onlyMixer()
    public {
        uint256 mixstatus = mix_details[mix_number].status;
        require(mixstatus == 1 || mixstatus == 2 || mixstatus == 3 || mixstatus == 5);
        mix_cleanup(mix_number);
    }

    
    /**
     * Internal function to clean up the transaction's various states. 
     * 
     * Cleans up a mix after it has successfully finished. This releases all the
     * various internal states.
     * 
     * This should ideally be called by the transaction_execute() function, since 
     * this function will release a lot of gas, and it has to get used up as a 
     * part of another transaction to make use of the gas refund.
     **/
    function mix_cleanup(uint256 mix_number) 
    internal {
        // Cleanup a mix and set it to the default state.
        // First, release all the sender locks
        uint256 i;
        for(i=0; i < mix_details[mix_number].senders.length; i++) {
            balances[mix_details[mix_number].senders[i].addr].locked = false;           
        }
        
        delete mix_details[mix_number].senders;
        delete mix_details[mix_number].recievers;       
        
        mix_details[mix_number].status = 0;
    }
}