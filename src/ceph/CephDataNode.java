package ceph;

import commands.CephCommand;
import commonmodels.*;
import commonmodels.transport.Request;
import ring.LookupTable;
import util.MathX;

import java.util.ArrayList;
import java.util.List;

public class CephDataNode extends DataNode {

    public CephDataNode() {
    }

    public CephDataNode(String ip, int port) {
        super(ip, port);
    }

    @Override
    public void createTerminal() {
        terminal = new CephTerminal(getAddress());
    }

    @Override
    public Object getTable() {
        return ClusterMap.getInstance();
    }

    @Override
    public long getEpoch() {
        return LookupTable.getInstance().getEpoch();
    }

    @Override
    public String updateTable(Object o) {
        return ClusterMap.getInstance().updateTable(o);
    }

    @Override
    public List<PhysicalNode> getPhysicalNodes() {
        return new ArrayList<>(
                ClusterMap.getInstance().getPhysicalNodeMap().values()
        );
    }

    @Override
    public Request prepareListPhysicalNodesCommand() {
        return new Request()
                .withHeader(CephCommand.LISTPHYSICALNODES.name());
    }

    @Override
    public Request prepareAddNodeCommand() {
        return prepareAddNodeCommand(ip, port);
    }

    @Override
    public Request prepareAddNodeCommand(String nodeIp, int nodePort) {
        List<Clusterable> edges = ClusterMap.getInstance().getSpareEdges();

        String clusterId = "";
        if (edges.size() > 0) {
            int index = MathX.nextInt(edges.size());
            clusterId = edges.get(index).getId();
        }

        String attachment = nodeIp + ":" + nodePort + " " + clusterId;
        return new Request()
                .withHeader(CephCommand.ADDNODE.name())
                .withAttachment(attachment.trim());
    }

    @Override
    public Request prepareRemoveNodeCommand(String nodeIp, int nodePort) {
        return new Request()
                .withHeader(CephCommand.REMOVENODE.name())
                .withAttachments(nodeIp + ":" + nodePort);
    }

    @Override
    public Request prepareLoadBalancingCommand(String... addresses) {
        int weight = MathX.nextInt((int)ClusterMap.getInstance().getRoot().getWeight());
        return new Request().withHeader(CephCommand.CHANGEWEIGHT.name())
                .withAttachment(addresses[0] + " " + weight);
    }

    @Override
    public Request prepareIncreaseLoadCommand(String... addresses) {
        int weight = MathX.nextInt((int)ClusterMap.getInstance().getRoot().getWeight());
        return new Request().withHeader(CephCommand.CHANGEWEIGHT.name())
                .withAttachment(addresses[0] + " " + weight);
    }

    @Override
    public Request prepareDecreaseLoadCommand(String... addresses) {
        int weight = -1 * MathX.nextInt((int)ClusterMap.getInstance().getRoot().getWeight());
        return new Request().withHeader(CephCommand.CHANGEWEIGHT.name())
                .withAttachment(addresses[0] + " " + weight);
    }

    @Override
    public LoadChangeHandler getLoadChangeHandler() {
        return new CephLoadChangeHandler(ClusterMap.getInstance());
    }

    @Override
    public void setLoadBalancingCallBack(LoadBalancingCallBack callBack) {
        ClusterMap.getInstance().setLoadBalancingCallBack(callBack);
    }

    @Override
    public void setMembershipCallBack(MembershipCallBack callBack) {
        ClusterMap.getInstance().setMembershipCallBack(callBack);
    }

    @Override
    public void setReadWriteCallBack(ReadWriteCallBack callBack) {
        ClusterMap.getInstance().setReadWriteCallBack(callBack);
    }

}
