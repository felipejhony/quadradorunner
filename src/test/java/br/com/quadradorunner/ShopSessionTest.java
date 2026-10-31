package br.com.quadradorunner;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShopSessionTest {
    @Test void shopAndPurchasesAreUnavailableBeforeVictoryAndWhenShopIsClosed() {
        GameSession session = session();
        GameProgress original = session.current().progress();
        for (GameModel.State state : List.of(GameModel.State.READY, GameModel.State.RUNNING,
                GameModel.State.PAUSED, GameModel.State.LOST)) {
            session.current().state = state;
            assertFalse(session.openShop());
            assertFalse(session.isShopOpen());
            assertEquals(AbilityShop.Status.UNAVAILABLE, session.buy(AbilityShop.Item.DASH));
            assertEquals(AbilityShop.Status.UNAVAILABLE, session.current().buy(AbilityShop.Item.DASH));
            assertSame(original, session.current().progress());
        }
        win(session);
        GameProgress won = session.current().progress();
        assertEquals(AbilityShop.Status.UNAVAILABLE, session.buy(AbilityShop.Item.DASH));
        assertSame(won, session.current().progress());
    }

    @Test void shopBlocksAdvanceAndPurchasedAbilityFollowsNextLevel() {
        GameSession session = session();
        win(session);
        assertTrue(session.openShop());
        assertFalse(session.canAdvance());
        assertFalse(session.advance());
        assertEquals(AbilityShop.Status.PURCHASED, session.buy(AbilityShop.Item.DASH));
        assertEquals(6, session.current().progress().balance());
        session.closeShop();
        GameProgress bought = session.current().progress();
        assertTrue(session.advance());
        assertFalse(session.isShopOpen());
        assertSame(bought, session.current().progress());
        assertEquals(Set.of(GameProgress.Ability.DASH), session.current().progress().unlockedAbilities());
        assertEquals(GameModel.State.READY, session.current().state);
        assertEquals(0, session.current().powerRemaining);
    }

    @Test void replayKeepsPurchaseAndCollectedRingsAndDoesNotChangeDoubleJump() {
        Ring ring = new Ring("one", 110, Level.GROUND - 30);
        GameProgress progress = new GameProgress(10, Set.of(new GameProgress.RingId("a", "one")),
            Set.of(), Set.of());
        GameSession session = new GameSession(List.of(new Level("a", List.of(), List.of(), List.of(ring))), progress);
        win(session);
        session.openShop();
        assertEquals(AbilityShop.Status.PURCHASED, session.buy(AbilityShop.Item.GLIDE));
        GameProgress bought = session.current().progress();
        session.replay();
        assertFalse(session.isShopOpen());
        assertSame(bought, session.current().progress());
        assertTrue(session.current().isRingCollected(ring));
        assertEquals(4, session.current().progress().balance());
        assertTrue(session.current().jump());
        assertFalse(session.current().jump());
        assertEquals(0, session.current().powerRemaining);
        assertFalse(session.openShop());
    }

    private GameSession session() {
        return new GameSession(List.of(new Level("a", List.of(), List.of(), List.of()),
            new Level("b", List.of(), List.of(), List.of())), new GameProgress(10, Set.of(), Set.of(), Set.of()));
    }

    private void win(GameSession session) {
        session.current().state = GameModel.State.RUNNING;
        session.current().x = Level.FINISH - 1;
        session.current().update(FixedStepLoop.STEP);
    }
}
