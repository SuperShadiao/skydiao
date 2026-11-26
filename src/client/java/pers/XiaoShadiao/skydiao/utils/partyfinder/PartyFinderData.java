package pers.XiaoShadiao.skydiao.utils.partyfinder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Util;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PartyFinderData implements Cloneable {

    public String requestKey = "";

    public UUID partyId;
    public String leaderPlayerName;
    public String description = "";

    public int maxPlayers = 2;
    public Set<String> players = new HashSet<>();

    public Type partyType = Type.DUNGEON;
    public Set<SubType> partySubTypes = new HashSet<>();

    public boolean isValid() {
        return partyType != null && ToolList.getInstance().stringHasContext(leaderPlayerName) && partyId != null && maxPlayers >= 2;
    }

    public List<SubType> getOrderedPartySubTypes() {
        List<SubType> orderedSubTypes = new ArrayList<>(partySubTypes);
        orderedSubTypes.sort(Comparator.comparingInt(PartyFinderData.SubType::ordinal));
        return orderedSubTypes;
    }

    public boolean alreadyInThisParty() {
        return players.contains(ToolList.mc.getUser().getName()) || Objects.equals(partyId, PartyFinderManager.selfPartyData.partyId);
    }

    public boolean canJoin() {
        return !isPartyFull() && !alreadyInThisParty();
    }

    public boolean isPartyFull() {
        return players.size() >= maxPlayers;
    }

    @Override
    public PartyFinderData clone() {
        try {
            PartyFinderData clone = (PartyFinderData) super.clone();
            clone.players = new HashSet<>(players);
            clone.partySubTypes = new HashSet<>(partySubTypes);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public JsonObject toJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("requestKey", requestKey);
        jsonObject.addProperty("partyId", partyId.toString());
        jsonObject.addProperty("leaderPlayerName", leaderPlayerName);
        jsonObject.addProperty("description", description);
        jsonObject.addProperty("maxPlayers", maxPlayers);
        jsonObject.add("players", Util.make(() -> {
            JsonArray jsonArray = new JsonArray();
            players.forEach(jsonArray::add);
            return jsonArray;
        }));
        jsonObject.addProperty("partyType", partyType.name());
        jsonObject.add("partySubTypes", Util.make(() -> {
            JsonArray jsonArray = new JsonArray();
            partySubTypes.stream().map(SubType::name).forEach(jsonArray::add);
            return jsonArray;
        }));
        return jsonObject;
    }

    public static PartyFinderData fromJson(JsonObject jsonObject) {
        PartyFinderData partyFinderData = new PartyFinderData();
        partyFinderData.requestKey = jsonObject.get("requestKey").getAsString();
        partyFinderData.partyId = UUID.fromString(jsonObject.get("partyId").getAsString());
        partyFinderData.leaderPlayerName = jsonObject.get("leaderPlayerName").getAsString();
        partyFinderData.description = jsonObject.get("description").getAsString();
        partyFinderData.maxPlayers = jsonObject.get("maxPlayers").getAsInt();
        partyFinderData.players = StreamSupport.stream(jsonObject.get("players").getAsJsonArray().spliterator(), false).map(JsonElement::getAsString).collect(Collectors.toSet());
        try {
            partyFinderData.partyType = Type.valueOf(jsonObject.get("partyType").getAsString());
        } catch (IllegalArgumentException e) {
            partyFinderData.partyType = Type.UNKNOWN;
        }
        partyFinderData.partySubTypes = StreamSupport.stream(jsonObject.get("partySubTypes").getAsJsonArray().spliterator(), false).map(JsonElement::getAsString)
                .map(subType -> {
                    try {
                        return SubType.valueOf(subType);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toSet());
        return partyFinderData;
    }

    public enum Type {
        DUNGEON("Dungeon", Color.RED, 5, false,List.of(
                SubType.F0, SubType.F1, SubType.F2, SubType.F3, SubType.F4, SubType.F5, SubType.F6, SubType.F7,
                SubType.M1, SubType.M2, SubType.M3, SubType.M4, SubType.M5, SubType.M6, SubType.M7, SubType.M7R, SubType.CHILL)),
        FISHING("Fishing", Color.GREEN, 2, true, List.of(SubType.BAYOU, SubType.LOTUS, SubType.SPOOKY, SubType.BARN, SubType.GALATEA, SubType.CANYON, SubType.ISLE, SubType.JERRY, SubType.CRYSTAL_HOLLOW)),
        DIANA("Diana", Color.YELLOW, 2, true, List.of(SubType.LEGENDARY_PET, SubType.MYTHIC_PET)),
        SAFARI("Safari", Color.GREEN, 4, false, List.of(SubType.SAFARI_FULL_CLEAR, SubType.SAFARI_UNIQUE_CLEAR)),
        KUUDRA("Kuudra", new Color(255, 106, 0), 4, false, List.of(SubType.KUUDRA_BASIC, SubType.KUUDRA_HOT, SubType.KUUDRA_BURNING, SubType.KUUDRA_FIERY, SubType.KUUDRA_INFERNAL, SubType.KUUDRA_DAILY)),
        RIFT("Rift", Color.magenta, 2, true, List.of(SubType.RIFT_CARRY, SubType.RIFT_LEECH, SubType.RIFT_SLIME, SubType.RIFT_VAMPIRE_SLAYER, SubType.RIFT_BURGER_CONTEST, SubType.RIFT_SNAKE)),
        RAVENGURAD("Ravenguard", Color.ORANGE, 3, false, List.of()),
        UNKNOWN("Unknown", Color.WHITE, 0, false, List.of())
        ;

        public final Color color;
        public final int recommendMaxPlayers;
        public final boolean recommendMoreMaxPlayers;
        public final List<SubType> subTypes;
        public final String displayName;

        Type(String displayName, Color color, int recommendMaxPlayers, boolean recommendMoreMaxPlayers, List<SubType> subTypes) {
            this.color = color;
            this.recommendMaxPlayers = recommendMaxPlayers;
            this.recommendMoreMaxPlayers = recommendMoreMaxPlayers;
            this.subTypes = subTypes;
            this.displayName = displayName;
        }

        public boolean isMaxPlayerRecommend(int maxPlayers) {
            if(recommendMoreMaxPlayers) {
                return maxPlayers >= recommendMaxPlayers;
            }
            return maxPlayers == recommendMaxPlayers;
        }

        public String getI18nDescription() {
            return CrowdinI18nManager.translate("features.partyfinder_description.type." + displayName);
        }
    }

    public enum SubType {
        F0("Entrance", Color.GREEN),
        F1("F1", Color.GREEN),
        F2("F2", Color.GREEN),
        F3("F3", Color.GREEN),
        F4("F4", Color.GREEN),
        F5("F5", Color.GREEN),
        F6("F6", Color.GREEN),
        F7("F7", Color.GREEN),
        M1("M1", Color.RED),
        M2("M2", Color.RED),
        M3("M3", Color.RED),
        M4("M4", Color.RED),
        M5("M5", Color.RED),
        M6("M6", Color.RED),
        M7("M7", Color.RED),
        M7R("M7R/ADG", new Color(255, 98, 0)),
        CHILL("Chill", Color.YELLOW),

        BAYOU("Bayou", new Color(0, 124, 0)),
        LOTUS("Lotus", new Color(225, 0, 255)),
        SPOOKY("Spooky", new Color(255, 168, 0)),
        BARN("Barn", new Color(255, 255, 0)),
        GALATEA("Galatea", new Color(0, 255, 0)),
        CANYON("Canyon", new Color(255, 128, 0)),
        ISLE("Crismon Isle", new Color(255, 0, 0)),
        JERRY("Jerry's Workshop", new Color(255, 0, 0)),
        CRYSTAL_HOLLOW("Crystal Hollow", new Color(255, 0, 255)),

        LEGENDARY_PET("Legendary Pet", new Color(255, 168, 0)),
        MYTHIC_PET("Mythic Pet", new Color(255, 0, 255)),

        SAFARI_UNIQUE_CLEAR("Unique Clear", new Color(94, 94, 94)),
        SAFARI_FULL_CLEAR("Full Clear", new Color(255, 255, 0)),

        RIFT_CARRY("Need Carry", new Color(0, 255, 0)),
        RIFT_LEECH("Leech", new Color(0, 125, 0)),
        RIFT_SLIME("Slime", new Color(0, 255, 0)),
        RIFT_VAMPIRE_SLAYER("Vampire Slayer", new Color(255, 0, 0)),
        RIFT_BURGER_CONTEST("Burger Contest", new Color(255, 255, 0)),
        RIFT_SNAKE("Snake", new Color(125, 125, 255)),

        KUUDRA_BASIC("Basic (T1)", new Color(255, 0, 0)),
        KUUDRA_HOT("Hot (T2)", new Color(255, 0, 0)),
        KUUDRA_BURNING("Burning (T3)", new Color(255, 0, 0)),
        KUUDRA_FIERY("Fiery (T4)", new Color(255, 0, 0)),
        KUUDRA_INFERNAL("Infernal (T5)", new Color(255, 0, 0)),
        KUUDRA_DAILY("Daily", new Color(255, 213, 0)),
        ;


        public final String displayName;
        public final Color color;

        SubType(String displayName, Color color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getI18nDescription() {
            return CrowdinI18nManager.translate("features.partyfinder_description.subtype." + displayName);
        }
    }

}
