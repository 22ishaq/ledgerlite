package dev.ledgerlite.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.ledgerlite.IntegrationTest;
import dev.ledgerlite.account.AccountService;
import dev.ledgerlite.account.CreateAccountRequest;
import dev.ledgerlite.common.Direction;
import dev.ledgerlite.common.LedgerException;

/** Goes straight to the service so we can fire a lot of requests at once. */
class ConcurrencyIT extends IntegrationTest {

    @Autowired
    private LedgerService ledger;

    @Autowired
    private AccountService accounts;

    private UUID wallet;
    private UUID sink;

    @BeforeEach
    void fundWallet() {
        UUID cash = open("Settlement", Direction.DEBIT);
        wallet = open("Wallet", Direction.CREDIT);
        sink = open("Merchant", Direction.CREDIT);
        ledger.pay(client.id(), "fund", payment(cash, wallet, 1_000));
    }

    @Test
    void parallelPaymentsNeverOverdraw() throws Exception {
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            String key = "spend-" + i;
            attempts.add(() -> {
                try {
                    ledger.pay(client.id(), key, payment(wallet, sink, 100));
                    return true;
                } catch (LedgerException e) {
                    if (!e.code().equals("insufficient_funds")) {
                        throw e;
                    }
                    return false;
                }
            });
        }

        long succeeded = runTogether(attempts).stream().filter(Boolean::booleanValue).count();

        assertThat(succeeded).isEqualTo(10);
        assertThat(accounts.get(client.id(), wallet).balance()).isZero();
        assertThat(accounts.get(client.id(), sink).balance()).isEqualTo(1_000);
    }

    @Test
    void sameKeySentInParallelOnlyPostsOnce() throws Exception {
        List<Callable<UUID>> retries = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            retries.add(() -> ledger.pay(client.id(), "double-click", payment(wallet, sink, 300)).transaction().id());
        }

        Set<UUID> ids = new HashSet<>(runTogether(retries));

        assertThat(ids).hasSize(1);
        assertThat(accounts.get(client.id(), wallet).balance()).isEqualTo(700);
    }

    private UUID open(String name, Direction normalBalance) {
        return accounts.open(client.id(), new CreateAccountRequest(name, "GBP", normalBalance, false)).id();
    }

    private static PaymentRequest payment(UUID from, UUID to, long amount) {
        return new PaymentRequest(from, to, amount, "GBP", null, null);
    }

    private static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
