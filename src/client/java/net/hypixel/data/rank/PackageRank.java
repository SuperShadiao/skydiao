package net.hypixel.data.rank;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum PackageRank {
   NONE(1),
   VIP(2),
   VIP_PLUS(3),
   MVP(4),
   MVP_PLUS(5);

   private static final Collection<PackageRank> VALUES = Arrays.asList(values());
   private static final Map<Integer, PackageRank> BY_ID = (Map<Integer, PackageRank>)VALUES.stream()
      .collect(Collectors.toMap(PackageRank::getId, Function.identity()));
   private final int id;

   public static Collection<PackageRank> getValues() {
      return VALUES;
   }

   public static Optional<PackageRank> getById(int id) {
      return Optional.ofNullable(BY_ID.get(id));
   }

   private PackageRank(int id) {
      this.id = id;
   }

   public int getId() {
      return this.id;
   }
}
