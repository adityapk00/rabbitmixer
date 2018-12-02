pragma solidity ^0.4.20;

contract Verifiers {
    
    address constant burn_address = 0xf17f52151EbEF6C7334FAD080c5704D77216b732;
    uint256 constant verifier_deposit_minimum = 1.0 ether;
    uint256 constant verifier_initiation_fee = 0.1 ether;
    uint256 constant penalize_amount = 0.1 ether;
    
    struct Verifier {
        address addr;
        uint256 deposit_amount;
    }
    
    Verifier[] verifiers;
    
    function verifier_add() public payable {
        require(msg.value >= verifier_deposit_minimum + verifier_initiation_fee);
        
        verifiers.push(Verifier(msg.sender, msg.value));
    }
    
    // Remove oneself from the verifier list. Callable by either the owner of the contract
    // Or by the verifier themselves.
    function verifier_remove(uint8 num) public {
        require(num < verifiers.length);
        require(msg.sender == verifiers[num].addr);
        require(verifiers[num].deposit_amount > 0);
        
        verifiers[num].addr.transfer(verifiers[num].deposit_amount);
        
        // Remove a verifier by replacing it with the last element and decreasing size
        verifiers[num] = verifiers[verifiers.length-1];
        delete verifiers[verifiers.length-1];
        verifiers.length--;
    }
    
    // Peanlize a verifier for doing bad things. 
    function verifier_penalize(uint8 num) public {
        verifiers[num].deposit_amount -= penalize_amount;
        burn_address.transfer(penalize_amount);
        
        if (verifiers[num].deposit_amount < verifier_deposit_minimum) {
            verifier_remove(num);
        }
    }
    
    function verifier_get(uint8 num) public view returns (address, uint256) {
        require(num < verifiers.length);
        return (verifiers[num].addr, verifiers[num].deposit_amount);
    }
}