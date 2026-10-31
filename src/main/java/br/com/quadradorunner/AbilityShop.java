package br.com.quadradorunner;

import java.util.HashSet;
import java.util.Objects;

/** Fixed catalog and atomic in-memory purchase rules, independent of UI and storage. */
final class AbilityShop {
    enum Item {
        DASH(GameProgress.Ability.DASH, "Dash", 4),
        GLIDE(GameProgress.Ability.GLIDE, "Planar", 6);

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

    static Purchase purchase(GameProgress progress, Item item) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(item, "item");
        if (progress.unlockedAbilities().contains(item.ability)) {
            return new Purchase(Status.ALREADY_OWNED, progress);
        }
        if (progress.balance() < item.price) {
            return new Purchase(Status.INSUFFICIENT_FUNDS, progress);
        }
        var abilities = new HashSet<>(progress.unlockedAbilities());
        abilities.add(item.ability);
        return new Purchase(Status.PURCHASED, new GameProgress(progress.balance() - item.price,
            progress.collectedRings(), abilities, progress.completedLevelIds()));
    }
}
