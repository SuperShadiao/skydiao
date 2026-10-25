package pers.XiaoShadiao.skydiao.utils.partyfinder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Util;
import pers.XiaoShadiao.skydiao.utils.ToolList;

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
        return players.contains(ToolList.mc.getUser().getName());
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
        partyFinderData.partyType = Type.valueOf(jsonObject.get("partyType").getAsString());
        partyFinderData.partySubTypes = StreamSupport.stream(jsonObject.get("partySubTypes").getAsJsonArray().spliterator(), false).map(JsonElement::getAsString).map(SubType::valueOf).collect(Collectors.toSet());
        return partyFinderData;
    }

    public enum Type {
        DUNGEON("Dungeon", Color.RED, List.of(
                SubType.F0, SubType.F1, SubType.F2, SubType.F3, SubType.F4, SubType.F5, SubType.F6, SubType.F7,
                SubType.M1, SubType.M2, SubType.M3, SubType.M4, SubType.M5, SubType.M6, SubType.M7, SubType.CHILL)),
        FISHING("Fishing", Color.GREEN, List.of(SubType.BAYOU, SubType.LOTUS, SubType.SPOOKY, SubType.GALATEA, SubType.CANYON, SubType.ISLE, SubType.CRYSTAL_HOLLOW)),
        DIANA("Diana", Color.YELLOW, List.of(SubType.LEGENDARY_PET, SubType.MYTHIC_PET)),
        SAFARI("Safari", Color.GREEN, List.of()),
        KUUDRA("Kuudra", new Color(255, 106, 0), List.of(SubType.KUUDRA_BASIC, SubType.KUUDRA_HOT, SubType.KUUDRA_BURNING, SubType.KUUDRA_FIERY, SubType.KUUDRA_INFERNAL))
        ;

        public final Color color;
        public final List<SubType> subTypes;
        public final String displayName;

        Type(String displayName, Color color, List<SubType> subTypes) {
            this.color = color;
            this.subTypes = subTypes;
            this.displayName = displayName;
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
        CHILL("Chill", Color.YELLOW),

        BAYOU("Bayou", new Color(0, 124, 0)),
        LOTUS("Lotus", new Color(225, 0, 255)),
        SPOOKY("Spooky", new Color(255, 168, 0)),
        GALATEA("Galatea", new Color(0, 255, 0)),
        CANYON("Canyon", new Color(255, 128, 0)),
        ISLE("Crismon Isle", new Color(255, 0, 0)),
        CRYSTAL_HOLLOW("Crystal Hollow", new Color(255, 0, 255)),

        LEGENDARY_PET("Legendary Pet", new Color(255, 168, 0)),
        MYTHIC_PET("MYTHIC Pet", new Color(255, 0, 255)),

        // Safari nothing

        KUUDRA_BASIC("Basic (T1)", new Color(255, 0, 0)),
        KUUDRA_HOT("Hot (T2)", new Color(255, 0, 0)),
        KUUDRA_BURNING("Burning (T3)", new Color(255, 0, 0)),
        KUUDRA_FIERY("Fiery (T4)", new Color(255, 0, 0)),
        KUUDRA_INFERNAL("Infernal (T5)", new Color(255, 0, 0)),
        ;


        public final String displayName;
        public final Color color;

        SubType(String displayName, Color color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

}
