package br.com.quadradorunner;

import java.util.HashSet;
import java.util.Objects;

/** Base-price catalog and atomic in-memory purchase rules, independent of UI and storage. */
final class AbilityShop {
    private static final int PRICE_INCREASE_PER_PURCHASE = 3;

    enum Item {
        DASH(GameProgress.Ability.DASH, "Dash", 4),
        GLIDE(GameProgress.Ability.GLIDE, "Planar", 6),
        DOUBLE_JUMP(GameProgress.Ability.DOUBLE_JUMP, "Pulo duplo", 5);

        final GameProgress.Ability ability;
        final String label;
        final int price;

        Item(GameProgress.Ability ability, String label, int price) {
            this.ability = ability;
            this.label = label;
            this.price = price;
        }
    }

    enum Status { PURCHASED, ALREADY_OWNED, INSUFFICIENT_FUNDS, UNAVAILABLE }
    record Purchase(Status status, GameProgress progress) { }

    private AbilityShop() { }

    static int price(GameProgress progress, Item item) {
        return item.price + PRICE_INCREASE_PER_PURCHASE * progress.unlockedAbilities().size();
    }

    static Purchase purchase(GameProgress progress, Item item) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(item, "item");
        if (progress.unlockedAbilities().contains(item.ability)) {
            return new Purchase(Status.ALREADY_OWNED, progress);
        }
        int price = price(progress, item);
        if (progress.balance() < price) {
            return new Purchase(Status.INSUFFICIENT_FUNDS, progress);
        }
        var abilities = new HashSet<>(progress.unlockedAbilities());
        abilities.add(item.ability);
        return new Purchase(Status.PURCHASED, new GameProgress(progress.balance() - price,
            progress.collectedRings(), abilities, progress.completedLevelIds()));
    }
}
