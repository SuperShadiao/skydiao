package net.hypixel.data.type;

import java.util.Arrays;
import java.util.Collection;

public enum LobbyType implements ServerType {
   MAIN("Main Lobby"),
   TOURNAMENT("Tournament Hall");

   private static final Collection<LobbyType> VALUES = Arrays.asList(values());
   private final String name;

   public static Collection<LobbyType> getValues() {
      return VALUES;
   }

   private LobbyType(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }
}
