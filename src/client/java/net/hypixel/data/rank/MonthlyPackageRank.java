package net.hypixel.data.rank;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum MonthlyPackageRank {
   NONE(1),
   SUPERSTAR(2);

   private static final Collection<MonthlyPackageRank> VALUES = Arrays.asList(values());
   private static final Map<Integer, MonthlyPackageRank> BY_ID = (Map<Integer, MonthlyPackageRank>)VALUES.stream()
      .collect(Collectors.toMap(MonthlyPackageRank::getId, Function.identity()));
   private final int id;

   public static Collection<MonthlyPackageRank> getValues() {
      return VALUES;
   }

   public static Optional<MonthlyPackageRank> getById(int id) {
      return Optional.ofNullable(BY_ID.get(id));
   }

   private MonthlyPackageRank(int id) {
      this.id = id;
   }

   public int getId() {
      return this.id;
   }
}
