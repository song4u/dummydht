package ring;

import commonmodels.BinarySearchList;
import commonmodels.Indexable;
import commonmodels.PhysicalNode;

import java.util.HashMap;
import java.util.Map;

public class LookupTable {

    private long epoch;

    private BinarySearchList table;

    private HashMap<String, PhysicalNode> physicalNodeMap;

    private RingLoadBalanceAlgorithm loadBalanceAlgorithm;

    private RingMembershipAlgorithm membershipAlgorithm;

    private RingReadWriteAlgorithm readWriteAlgorithm;

    private static volatile LookupTable instance = null;

    private LookupTable() {
        initialize();
    }

    public static LookupTable getInstance() {
        if (instance == null) {
            synchronized(LookupTable.class) {
                if (instance == null) {
                    instance = new LookupTable();
                }
            }
        }

        return instance;
    }

    public void initialize() {
        physicalNodeMap = new HashMap<>();
        table = new BinarySearchList();
        epoch = System.currentTimeMillis();

        membershipAlgorithm = new RingMembershipAlgorithm();
        loadBalanceAlgorithm = new RingLoadBalanceAlgorithm();
        readWriteAlgorithm = new RingReadWriteAlgorithm();

        membershipAlgorithm.initialize(this);
    }

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    public BinarySearchList getTable() {
        return table;
    }

    public void setTable(BinarySearchList table) {
        this.table = table;
    }

    public HashMap<String, PhysicalNode> getPhysicalNodeMap() {
        return physicalNodeMap;
    }

    public void setPhysicalNodeMap(HashMap<String, PhysicalNode> physicalNodeMap) {
        this.physicalNodeMap = physicalNodeMap;
    }

    public void update() {
        epoch = System.currentTimeMillis();
    }

    public void addNode(Indexable node) {
        Indexable index = table.find(node); // where the new node is inserted to
        table.add(index.getIndex(), node); // only add the node to table, not gossiping the change yet
        loadBalanceAlgorithm.nodeJoin(this, index);
    }

    public void removeNode(Indexable node) {
        Indexable index = table.find(node); // where the new node is inserted to
        table.remove(index.getIndex()); // only remove from table, not gossiping the change yet
        loadBalanceAlgorithm.nodeLeave(this, index);
    }

    public void addNode(PhysicalNode node) {
        membershipAlgorithm.addPhysicalNode(this, node);
        update(); // commit the change, gossip to other nodes
    }

    public void removeNode(PhysicalNode node) {
        membershipAlgorithm.removePhysicalNode(this, node);
        update(); // commit the change, gossip to other nodes
    }

    public void increaseLoad(PhysicalNode node) {
        loadBalanceAlgorithm.increaseLoad(this, node);
        update(); // commit the change, gossip to other nodes
    }

    public void decreaseLoad(PhysicalNode node) {
        loadBalanceAlgorithm.decreaseLoad(this, node);
        update(); // commit the change, gossip to other nodes
    }

    public Indexable write(String filename) {
        return readWriteAlgorithm.write(this, filename);
    }

    public Indexable read(String filename) {
        return readWriteAlgorithm.read(this, filename);
    }

    public String listPhysicalNodes() {
        StringBuilder result = new StringBuilder();

        for(Map.Entry entry : physicalNodeMap.entrySet()) {
            result.append(entry.getValue().toString()).append('\n');
        }

        return result.toString();
    }

    @Override
    public String toString() {
        return "Epoch: " + epoch + "\n" + table.toString();
    }
}