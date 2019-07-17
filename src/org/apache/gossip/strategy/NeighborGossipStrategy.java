package org.apache.gossip.strategy;

import org.apache.gossip.LocalMember;
import org.apache.gossip.Member;
import org.apache.gossip.event.GossipState;
import org.apache.gossip.manager.GossipManager;

import java.util.*;
import java.util.stream.Collectors;

public class NeighborGossipStrategy extends GossipStrategy{

    protected NeighborGossipStrategy(GossipManager gossipManager) {
        super(gossipManager);
    }

    @Override
    public List<LocalMember> getGossipMembers() {
        List<LocalMember> liveMembers = new ArrayList<>(gossipManager.getLiveMembers());
        sort(liveMembers);
        return neighbors(liveMembers);
    }

    @Override
    public Set<Map.Entry<LocalMember, GossipState>> getWatchMemberSet() {
        List<LocalMember> allMembers = new ArrayList<>(gossipManager.getMembers().keySet());
        sort(allMembers);
        List<LocalMember> neighborList = neighbors(allMembers);
        return gossipManager.getMembers().entrySet().stream()
                .filter(entry -> neighborList.contains(entry.getKey()))
                .collect(Collectors.toSet());
    }

    private List<LocalMember> neighbors(List<LocalMember> members){
        int numOfNeighbors = (int) Math.ceil(Math.sqrt(members.size()) / 2);

        int index = members.indexOf(gossipManager.getMyself());
        if (index < 0) {
            return Collections.emptyList();
        }

        List<LocalMember> neighbourList = new ArrayList<>();
        int pre;
        int next;
        for (int i = 1; i < numOfNeighbors + 1; i++) {
            pre = index - i;
            next = index + i;
            if (pre < 0) pre = members.size() - 1;
            if (next >= members.size()) next = 0;

            neighbourList.add(members.get(pre));
            neighbourList.add(members.get(next));
        }

        return neighbourList;
    }

    private void sort(List<LocalMember> members) {
        members.add(gossipManager.getMyself());
        members.sort(Comparator.comparing(Member::getId));
    }
}
