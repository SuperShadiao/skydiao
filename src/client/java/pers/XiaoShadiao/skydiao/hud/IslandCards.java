package pers.XiaoShadiao.skydiao.hud;

import java.util.List;

public final class IslandCards {

    private static List<IslandCard> cards;

    private IslandCards() {
    }

    public static List<IslandCard> cards() {
        if (cards == null) {
            cards = List.of(new AlertCard(), new MaskCard(), new SlayerCard());
        }
        return cards;
    }

    public static IslandCard active() {
        IslandCard best = null;
        for (IslandCard card : cards()) {
            if (!card.visible()) continue;
            if (best == null || card.priority() > best.priority()) best = card;
        }
        return best;
    }
}
