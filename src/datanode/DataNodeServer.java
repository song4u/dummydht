package datanode;

import ceph.CephDataNode;
import commonmodels.*;
import commonmodels.transport.InvalidRequestException;
import commonmodels.transport.Request;
import commonmodels.transport.Response;
import datanode.strategies.CentralizedStrategy;
import datanode.strategies.DistributedStrategy;
import datanode.strategies.MembershipStrategy;
import elastic.ElasticDataNode;
import ring.RingDataNode;
import util.Config;

import java.util.List;

public class DataNodeServer {

    private DataNode dataNode;

    private MembershipStrategy membershipStrategy;

    public DataNodeServer(String ip, int port) throws Exception {
        initDataNode(ip, port);
        initStrategy();
    }

    private void initDataNode(String ip, int port) throws Exception {
        String scheme = Config.getInstance().getScheme();

        switch (scheme) {
            case Config.SCHEME_RING:
                dataNode = new RingDataNode(ip, port);
                break;
            case Config.SCHEME_ELASTIC:
                dataNode = new ElasticDataNode(ip, port);
                break;
            case Config.SCHEME_CEPH:
                dataNode = new CephDataNode(ip, port);
                break;
            default:
                throw new Exception("Invalid DHT type");
        }

        dataNode.setUseDynamicAddress(true);
    }

    private void initStrategy() {
        if (dataNode.getMode().equals(Config.MODE_DISTRIBUTED)) {
            membershipStrategy = new DistributedStrategy(dataNode);
        }
        else {
            membershipStrategy = new CentralizedStrategy(dataNode);
        }
    }

    public void start() throws Exception {
        membershipStrategy.onNodeStarted();
    }

    public void stop() {
        membershipStrategy.onNodeStopped();
        dataNode.destroy();
    }

    public Response getMembersStatus() {
        return membershipStrategy.getMembersStatus();
    }

    public Object getDataNodeTable() {
        return dataNode.getTable();
    }

    public List<PhysicalNode> getPhysicalNodes() {
        return dataNode.getPhysicalNodes();
    }

    public String updateTable(Object o) {
        return dataNode.updateTable(o);
    }

    public Response processCommand(String[] args) throws InvalidRequestException {
        return dataNode.execute(args);
    }

    public Response processCommand(Request request) throws InvalidRequestException {
        return dataNode.execute(request);
    }

    public DataNode getDataNode() {
        return dataNode;
    }

    public void setMembershipCallBack(MembershipCallBack callBack) {
        dataNode.setMembershipCallBack(callBack);
    }

    public void setReadWriteCallBack(ReadWriteCallBack callBack) {
        dataNode.setReadWriteCallBack(callBack);
    }

    public MembershipStrategy getMembershipStrategy() {
        return membershipStrategy;
    }
}
