package net.hypixel.data.region;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Environment {
   PRODUCTION(0),
   BETA(1),
   TEST(2);

   private static final Collection<Environment> VALUES = Arrays.asList(values());
   private static final Map<Integer, Environment> BY_ID = (Map<Integer, Environment>)VALUES.stream()
      .collect(Collectors.toMap(Environment::getId, Function.identity()));
   private final int id;

   public static Collection<Environment> getValues() {
      return VALUES;
   }

   public static Optional<Environment> getById(int id) {
      return Optional.ofNullable(BY_ID.get(id));
   }

   private Environment(int id) {
      this.id = id;
   }

   public int getId() {
      return this.id;
   }
}
