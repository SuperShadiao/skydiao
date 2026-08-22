package pers.XiaoShadiao.skydiao.utils;

import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PartyManager {

    public record Member(String name, ClientboundPartyInfoPacket.PartyMember hypInstance) {}

    private volatile static Map<UUID, Member> members = new HashMap<>();

    public static List<Member> getPartyMembers() {
        return List.copyOf(members.values());
    }

    public static boolean isInParty() {
        return !members.isEmpty();
    }

    public static void updatePartyMembers(List<ClientboundPartyInfoPacket.PartyMember> hypMembers) {
        Map<UUID, Member> oldMembers = members;
        ToolList.addThreadedTask(() -> {
            Map<UUID, Member> newMembers = new HashMap<>();
            for (ClientboundPartyInfoPacket.PartyMember hyp : hypMembers) {
                UUID uuid = hyp.getUuid();
                Member cached = oldMembers.get(uuid);
                if (cached != null) {
                    newMembers.put(uuid, new Member(cached.name(), hyp));
                } else {
                    try {
                        String name = UUIDLookup.getNameByUUID(uuid).get();
                        newMembers.put(uuid, new Member(name, hyp));
                    } catch (Exception e) {
                        ToolList.getInstance().log.catching(e);
                        newMembers.put(uuid, new Member(uuid.toString(), hyp));
                    }
                }
            }
            synchronized (PartyManager.class) {
                members = newMembers;
            }
        }, null);
    }

}
