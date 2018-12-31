package ring;

import commonmodels.Indexable;
import commonmodels.PhysicalNode;
import filemanagement.LocalFileManager;
import util.Config;
import util.MathX;
import util.SimpleLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class RingMembershipAlgorithm {

    public void initialize(LookupTable table) {
        SimpleLog.i("Initializing table...");
        
        Config config = Config.getInstance();

        int numberOfHashSlots = config.getNumberOfHashSlots();
        String startIp = config.getStartIp();
        int ipRange = config.getIpRange();
        int startPort = config.getStartPort();
        int portRange = config.getPortRange();
        int numberOfPhysicalNodes = config.getNumberOfPhysicalNodes();
        int virtualPhysicalRatio = config.getVirtualPhysicalRatio();

        int lastDot = startIp.lastIndexOf(".") + 1;
        String ipPrefix = startIp.substring(0, lastDot);
        int intStartIp = Integer.valueOf(startIp.substring(lastDot));

        int totalNodes = numberOfPhysicalNodes * virtualPhysicalRatio;
        Queue<Integer> ipPool = MathX.nonrepeatRandom(ipRange, numberOfPhysicalNodes);
        Queue<Integer> portPool = MathX.nonrepeatRandom(portRange, numberOfPhysicalNodes);
        Queue<Integer> hashPool = MathX.nonrepeatRandom(numberOfHashSlots, totalNodes);

        while (!ipPool.isEmpty()){
            Integer ip = ipPool.poll();
            Integer port = portPool.poll();
            assert port != null;

            PhysicalNode node = new PhysicalNode();
            node.setAddress(ipPrefix + (intStartIp + ip));
            node.setPort(startPort + port);
            table.getPhysicalNodeMap().put(node.getId(), node);

            for (int i = 0; i < virtualPhysicalRatio; i++) {
                Integer hash = hashPool.poll();
                assert hash != null;

                VirtualNode vnode = new VirtualNode(hash, node.getId());
                node.getVirtualNodes().add(vnode);
                table.getTable().add(vnode);
            }
        }

        SimpleLog.i("Allocating files...");
        LocalFileManager.getInstance().generateFileBuckets(numberOfHashSlots);
        SimpleLog.i("Files allocated...");

        SimpleLog.i("Table initialized...");
    }

    public void addPhysicalNode(LookupTable table, PhysicalNode node) {
        if (table.getPhysicalNodeMap().containsKey(node.getId())) {
            SimpleLog.i(node.getId() + " already exists. Try a different ip:port");
            return;
        }

        SimpleLog.i("Adding new physical node: " + node.toString() + "...");
        table.getPhysicalNodeMap().put(node.getId(), node);

        int[] hashPool = generateSpareBuckets(table);
        for (int hash : hashPool) {
            VirtualNode vnode = new VirtualNode(hash, node.getId());
            node.getVirtualNodes().add(vnode);
            table.addNode(vnode);
        }

        SimpleLog.i("Physical node added...");
    }

    public void addPhysicalNode(LookupTable table, PhysicalNode node, int[] buckets) {
        if (buckets == null || buckets.length == 0) {
            addPhysicalNode(table, node);
            return;
        }

        if (table.getPhysicalNodeMap().containsKey(node.getId())) {
            SimpleLog.i(node.getId() + " already exists. Try a different ip:port");
            return;
        }

        SimpleLog.i("Adding new physical node: " + node.toString() + "...");
        table.getPhysicalNodeMap().put(node.getId(), node);

        for (Integer hash : buckets) {
            VirtualNode vnode = new VirtualNode(hash, node.getId());
            node.getVirtualNodes().add(vnode);
            table.addNode(vnode);
        }

        SimpleLog.i("Physical node added...");
    }

    public int[] generateSpareBuckets(LookupTable table) {
        List<Integer> usedSlots = new ArrayList<>();
        for (int i = 0; i < table.getTable().size(); i++) {
            usedSlots.add(table.getTable().get(i).getHash());
        }
        Queue<Integer> hashPool = MathX.nonrepeatRandom(Config.getInstance().getNumberOfHashSlots(), Config.getInstance().getVirtualPhysicalRatio(), usedSlots);

        return hashPool.stream().mapToInt(Integer::intValue).toArray();
    }

    public void removePhysicalNode(LookupTable table, PhysicalNode node) {
        SimpleLog.i("Remove physical node: " + node.toString() + "...");

        PhysicalNode pnode = table.getPhysicalNodeMap().get(node.getId());
        if (pnode == null) {
            SimpleLog.i(node.getId() + " does not exist.");
            return;
        }

        for (Indexable vnode : pnode.getVirtualNodes()) {
            table.removeNode(vnode);
        }

        table.getPhysicalNodeMap().remove(node.getId());

        SimpleLog.i("Physical node removed...");
    }
}
