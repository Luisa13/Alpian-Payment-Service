package ch.alpian.payment.domain;

import java.util.UUID;

/**
 * Account aggregate root. Pure domain object: persistence lives in infrastructure.
 *
 * <p>{@code balance} is the booked amount; {@code availableBalance} is what can still be spent
 * (balance minus funds on hold). Invariant: {@code 0 <= availableBalance <= balance}.
 */
public class Account {

    private final UUID id;
    private final String ownerName;
    private final AccountType type;
    private Money balance;
    private Money availableBalance;
    private final long version;

    public Account(
            UUID id, String ownerName, AccountType type, Money balance, Money availableBalance, long version) {
        if (availableBalance.isGreaterThan(balance)) {
            throw new IllegalArgumentException("available balance must not exceed balance");
        }
        this.id = id;
        this.ownerName = ownerName;
        this.type = type;
        this.balance = balance;
        this.availableBalance = availableBalance;
        this.version = version;
    }

    public boolean canHold(Money amount) {
        return !availableBalance.isLessThan(amount);
    }

    /** Puts funds on hold: available balance goes down, balance is untouched. */
    public void hold(Money amount) {
        requirePositive(amount);
        if (!canHold(amount)) {
            throw new InsufficientFundsException(id, amount, availableBalance);
        }
        availableBalance = availableBalance.subtract(amount);
    }

    /** Gives a hold back: available balance goes up. */
    public void release(Money amount) {
        requirePositive(amount);
        if (availableBalance.add(amount).isGreaterThan(balance)) {
            throw new IllegalStateException("cannot release more than is held on account " + id);
        }
        availableBalance = availableBalance.add(amount);
    }

    /** Turns a hold into a debit: balance goes down, available balance is already reduced. */
    public void settleDebit(Money amount) {
        requirePositive(amount);
        if (held().isLessThan(amount)) {
            throw new IllegalStateException("cannot settle more than is held on account " + id);
        }
        balance = balance.subtract(amount);
    }

    /** Incoming money: both balances go up. */
    public void credit(Money amount) {
        requirePositive(amount);
        balance = balance.add(amount);
        availableBalance = availableBalance.add(amount);
    }

    public Money held() {
        return balance.subtract(availableBalance);
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return balance.currency();
    }

    public Money getBalance() {
        return balance;
    }

    public Money getAvailableBalance() {
        return availableBalance;
    }

    public long getVersion() {
        return version;
    }
}
