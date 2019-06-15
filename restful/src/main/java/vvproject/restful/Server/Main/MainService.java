package vvproject.restful.Server.Main;

import com.google.common.hash.Hashing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import vvproject.restful.Server.Clothing.Clothing;
import vvproject.restful.Server.Clothing.ClothingExceptions.ClothingNotFoundException;
import vvproject.restful.Server.Clothing.ClothingExceptions.InsufficientFundsException;
import vvproject.restful.Server.Clothing.ClothingService;
import vvproject.restful.Server.Enums.ClothingStatus;
import vvproject.restful.Server.Main.MainExceptions.MaxSellingSizeException;
import vvproject.restful.Server.Main.MainExceptions.WrongPricingException;
import vvproject.restful.Server.Member.Member;
import vvproject.restful.Server.Member.MemberExceptions.MemberNotFoundException;
import vvproject.restful.Server.Member.MemberExceptions.WrongLoginException;
import vvproject.restful.Server.Member.MemberService;
import vvproject.restful.Server.Transaction.Transaction;
import vvproject.restful.Server.Transaction.TransactionService;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service("MainService")
public class MainService {
    private MemberService memberService;
    private TransactionService transactionService;
    private ClothingService clothingService;

    @Autowired
    public MainService(MemberService memberService, TransactionService transactionService, ClothingService clothingService) {
        this.memberService = memberService;
        this.transactionService = transactionService;
        this.clothingService = clothingService;
    }

    public ResponseEntity<Void> removeClothingFromAccount(Long clothingId, String username, String password) throws MemberNotFoundException, WrongLoginException, ClothingNotFoundException {
        Member owner = this.memberService.login(username, password);
        Clothing toRemove = this.clothingService.findById(clothingId);
        owner.removeClothing(toRemove);
        this.memberService.updateMember(owner);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    public ResponseEntity<Clothing> findClothingById(Long id) throws ClothingNotFoundException {
        return new ResponseEntity<>(this.clothingService.findById(id), HttpStatus.OK);
    }

    public ResponseEntity<List<Clothing>> findAllClothing() {
        return new ResponseEntity<>(this.clothingService.findAll(), HttpStatus.OK);
    }

    public ResponseEntity<Void> buyClothing(Long id, String username, String password) throws ClothingNotFoundException, InsufficientFundsException, MemberNotFoundException, WrongLoginException {
        Member buyer = this.memberService.login(username, password);
        Clothing clothingToBePurchased = this.clothingService.findById(id);
        Member seller = clothingToBePurchased.getOwner();
        if (buyer.getAccountBalance() < (clothingToBePurchased.getExchangePrice() + 0.5f))
            throw new InsufficientFundsException("Insufficient funds");

        buyer.removeBalance(clothingToBePurchased.getExchangePrice() - 0.5f);
        seller.addBalance(clothingToBePurchased.getExchangePrice() - 0.5f);
        seller.removeClothing(clothingToBePurchased);


        clothingToBePurchased.setClothingStatus(ClothingStatus.SOLD);
        Transaction transaction = new Transaction(clothingToBePurchased, buyer, seller);

        this.clothingService.updateClothing(clothingToBePurchased);
        this.memberService.updateMember(buyer);
        this.memberService.updateMember(seller);
        this.transactionService.saveTransaction(transaction);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    public ResponseEntity<Void> sellClothing(Clothing c, String username, String password) throws WrongPricingException, MaxSellingSizeException, MemberNotFoundException, WrongLoginException {
        Member seller = this.memberService.login(username, password);
        if ((c.getExchangePrice() / c.getOriginalPrice()) > 0.5 || (c.getExchangePrice() / c.getOriginalPrice()) < 0.1)
            throw new WrongPricingException("Exchange price should be between 10 and 50 percent of the original price.");

        if (seller.getOwnedClothingSize() >= 10)
            throw new MaxSellingSizeException("You can only sell 10 items at a time.");

        Clothing clothing = new Clothing(
                c.getGender(),
                c.getType(),
                c.getSize(),
                c.getOriginalPrice(),
                c.getExchangePrice(),
                seller
        );

        seller.addClothing(clothing);
        this.clothingService.saveClothing(clothing);
        this.memberService.updateMember(seller);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    public ResponseEntity<Void> register(Member m) {
        Member member = new Member(
                m.getUsername(),
                Hashing.sha256().hashString(m.getPassword(), StandardCharsets.UTF_8).toString(),
                m.getPreName(),
                m.getLastName(),
                m.geteMail(),
                m.getPostTown(),
                m.getAddress(),
                m.getPostCode(),
                m.getVersion(),
                m.getAccountBalance()

        );
        this.memberService.saveMember(member);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return new ResponseEntity<>(this.transactionService.findAllTransactions(), HttpStatus.OK);
    }
}