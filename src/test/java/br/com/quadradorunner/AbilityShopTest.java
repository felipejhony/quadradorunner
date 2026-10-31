package br.com.quadradorunner;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbilityShopTest {
    @Test void validPurchaseDebitsExactPriceAndPreservesHistoryWithoutMutatingOriginal() {
        GameProgress original = new GameProgress(10, Set.of(new GameProgress.RingId("a", "one")),
            Set.of(GameProgress.Ability.GLIDE), Set.of("a"));
        AbilityShop.Purchase purchase = AbilityShop.purchase(original, AbilityShop.Item.DASH);
        assertEquals(AbilityShop.Status.PURCHASED, purchase.status());
        assertEquals(3, purchase.progress().balance());
        assertEquals(Set.of(GameProgress.Ability.DASH, GameProgress.Ability.GLIDE),
            purchase.progress().unlockedAbilities());
        assertEquals(original.collectedRings(), purchase.progress().collectedRings());
        assertEquals(original.completedLevelIds(), purchase.progress().completedLevelIds());
        assertEquals(10, original.balance());
        assertEquals(Set.of(GameProgress.Ability.GLIDE), original.unlockedAbilities());
    }

    @Test void exactBalanceIsAcceptedAndInsufficientBalanceLeavesSnapshotUnchanged() {
        for (AbilityShop.Item item : AbilityShop.Item.values()) {
            GameProgress exact = new GameProgress(item.price, Set.of(), Set.of(), Set.of());
            AbilityShop.Purchase purchased = AbilityShop.purchase(exact, item);
            assertEquals(AbilityShop.Status.PURCHASED, purchased.status());
            assertEquals(0, purchased.progress().balance());
            for (int balance : List.of(0, item.price - 1)) {
                GameProgress shortfall = new GameProgress(balance, Set.of(), Set.of(), Set.of());
                AbilityShop.Purchase rejected = AbilityShop.purchase(shortfall, item);
                assertEquals(AbilityShop.Status.INSUFFICIENT_FUNDS, rejected.status());
                assertSame(shortfall, rejected.progress());
            }
        }
    }

    @Test void duplicatePurchaseNeverDebitsAgainEvenAfterBalanceWasSpent() {
        GameProgress progress = AbilityShop.purchase(new GameProgress(4, Set.of(), Set.of(), Set.of()),
            AbilityShop.Item.DASH).progress();
        for (int i = 0; i < 3; i++) {
            AbilityShop.Purchase duplicate = AbilityShop.purchase(progress, AbilityShop.Item.DASH);
            assertEquals(AbilityShop.Status.ALREADY_OWNED, duplicate.status());
            assertSame(progress, duplicate.progress());
            assertEquals(0, duplicate.progress().balance());
        }
    }

    @Test void catalogFitsCurrentRingSupplyAndRejectsMissingArguments() {
        assertTrue(AbilityShop.Item.DASH.price > 0);
        assertTrue(AbilityShop.Item.GLIDE.price > 0);
        assertTrue(AbilityShop.Item.DASH.price + AbilityShop.Item.GLIDE.price <= Level.standard().rings.size());
        assertThrows(NullPointerException.class, () -> AbilityShop.purchase(null, AbilityShop.Item.DASH));
        assertThrows(NullPointerException.class, () -> AbilityShop.purchase(new GameProgress(), null));
    }
}
