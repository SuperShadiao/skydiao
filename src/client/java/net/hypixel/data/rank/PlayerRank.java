package net.hypixel.data.rank;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum PlayerRank {
   NORMAL(1),
   YOUTUBER(2),
   GAME_MASTER(3),
   ADMIN(4);

   private static final Collection<PlayerRank> VALUES = Arrays.asList(values());
   private static final Map<Integer, PlayerRank> BY_ID = (Map<Integer, PlayerRank>)VALUES.stream()
      .collect(Collectors.toMap(PlayerRank::getId, Function.identity()));
   private final int id;

   public static Collection<PlayerRank> getValues() {
      return VALUES;
   }

   public static Optional<PlayerRank> getById(int id) {
      return Optional.ofNullable(BY_ID.get(id));
   }

   private PlayerRank(int id) {
      this.id = id;
   }

   public int getId() {
      return this.id;
   }
}
