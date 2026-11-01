package br.com.quadradorunner;

import java.util.HashSet;
import java.util.Objects;

/** Base-price catalog and atomic in-memory purchase rules, independent of UI and storage. */
final class AbilityShop {
    private static final int PRICE_INCREASE_PER_PURCHASE = 3;

    enum Item {
        DASH(GameProgress.Ability.DASH, "Dash", 4),
        GLIDE(GameProgress.Ability.GLIDE, "Planar", 6),
        DOUBLE_JUMP(GameProgress.Ability.DOUBLE_JUMP, "Pulo duplo", 5),
        DURABILITY(GameProgress.Ability.DURABILITY, "Durabilidade", 5),
        ROCKET(GameProgress.Ability.ROCKET, "Foguete", 5),
        DASH_UPGRADE(GameProgress.Ability.DASH_UPGRADE, "Dash II · recarga pela metade", 3),
        DOUBLE_JUMP_UPGRADE(GameProgress.Ability.DOUBLE_JUMP_UPGRADE, "Pulo duplo II · recarga pela metade", 3);

        final GameProgress.Ability ability;
        final String label;
        final int price;

        Item(GameProgress.Ability ability, String label, int price) {
            this.ability = ability;
            this.label = label;
            this.price = price;
        }
    }

    enum Status { PURCHASED, ALREADY_OWNED, INSUFFICIENT_FUNDS, REQUIRES_BASE, UNAVAILABLE }
    record Purchase(Status status, GameProgress progress) { }

    private AbilityShop() { }

    static int price(GameProgress progress, Item item) {
        if (item.ability.prerequisite() != null) return item.price;
        long basePurchases = progress.unlockedAbilities().stream().filter(a -> a.prerequisite() == null).count();
        return item.price + PRICE_INCREASE_PER_PURCHASE * (int) basePurchases;
    }

    static Purchase purchase(GameProgress progress, Item item) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(item, "item");
        if (progress.unlockedAbilities().contains(item.ability)) {
            return new Purchase(Status.ALREADY_OWNED, progress);
        }
        if (item.ability.prerequisite() != null
                && !progress.unlockedAbilities().contains(item.ability.prerequisite())) {
            return new Purchase(Status.REQUIRES_BASE, progress);
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
