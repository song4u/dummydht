package elastic;

import commonmodels.PhysicalNode;
import filemanagement.FileTransferManager;
import util.Config;
import util.SimpleLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class ElasticLoadBalanceAlgorithm {

    public void moveBucket(LookupTable lookupTable, BucketNode node, PhysicalNode from, PhysicalNode to) {
        SimpleLog.i("Moving bucket [" + node.getHash() + "] from " + from.getId() + " to " + to.getId());

        PhysicalNode fromNode = lookupTable.getPhysicalNodeMap().get(from.getId());
        if (fromNode == null) {
            SimpleLog.i(from.getId() + " does not exist.");
            return;
        }
        else if (!fromNode.getVirtualNodes().contains(node)){
            SimpleLog.i(from.getId() + " does not have bucket [" + node.getHash() + "]");
            return;
        }

        PhysicalNode toNode = lookupTable.getPhysicalNodeMap().get(to.getId());
        if (toNode == null) {
            SimpleLog.i(to.getId() + " does not exist.");
            return;
        }
        else if (toNode.getVirtualNodes().contains(node)){
            SimpleLog.i(to.getId() + " already have bucket [" + node.getHash() + "]");
            return;
        }

        node = lookupTable.getTable()[node.getHash()];
        node.getPhysicalNodes().remove(fromNode.getId());
        fromNode.getVirtualNodes().remove(node);
        node.getPhysicalNodes().add(toNode.getId());
        toNode.getVirtualNodes().add(node);

        String token = UUID.randomUUID().toString();
        requestTransfer(node, from ,to, token);

        SimpleLog.i("Moving bucket [" + node.getHash() + "] from " + from.getId() + " to " + to.getId());
        SimpleLog.i("Updated bucket info: " + node.toString());
        SimpleLog.i("Updated " + fromNode.getId() + " info: " + fromNode.toString());
        SimpleLog.i("Updated " + toNode.getId() + " info: " + toNode.toString());
    }

    public void moveBuckets(LookupTable lookupTable, List<BucketNode> nodes, PhysicalNode from, PhysicalNode to) {
        SimpleLog.i("Moving buckets  from " + from.getId() + " to " + to.getId());

        PhysicalNode fromNode = lookupTable.getPhysicalNodeMap().get(from.getId());
        if (fromNode == null) {
            SimpleLog.i(from.getId() + " does not exist.");
            return;
        }

        PhysicalNode toNode = lookupTable.getPhysicalNodeMap().get(to.getId());
        if (toNode == null) {
            SimpleLog.i(to.getId() + " does not exist.");
            return;
        }

        String token = UUID.randomUUID().toString();
        List<Integer> bucketsToTransfer = new ArrayList<>();
        for (BucketNode bucketNode : nodes) {
            if (!fromNode.getVirtualNodes().contains(bucketNode)){
                SimpleLog.i(from.getId() + " does not have bucket [" + bucketNode.getHash() + "]");
                continue;
            }
            if (toNode.getVirtualNodes().contains(bucketNode)){
                SimpleLog.i(to.getId() + " already have bucket [" + bucketNode.getHash() + "]");
                continue;
            }

            bucketNode = lookupTable.getTable()[bucketNode.getHash()];
            bucketNode.getPhysicalNodes().remove(fromNode.getId());
            fromNode.getVirtualNodes().remove(bucketNode);
            bucketNode.getPhysicalNodes().add(toNode.getId());
            toNode.getVirtualNodes().add(bucketNode);
            bucketsToTransfer.add(bucketNode.getHash());
        }

        requestTransfer(bucketsToTransfer, from ,to, token);

        SimpleLog.i("Moving buckets [" + nodes + "] from " + from.getId() + " to " + to.getId());
        SimpleLog.i("Updated buckets info: " + nodes.toString());
        SimpleLog.i("Updated " + fromNode.getId() + " info: " + fromNode.toString());
        SimpleLog.i("Updated " + toNode.getId() + " info: " + toNode.toString());
    }

    public void copyBucket(LookupTable lookupTable, BucketNode node, PhysicalNode to) {
        Random random = new Random();
        int index = random.nextInt(node.getPhysicalNodes().size());
        requestReplication(node,
                lookupTable.getPhysicalNodeMap().get(node.getPhysicalNodes().get(index)),
                to);
    }

    public void transferBucket(LookupTable lookupTable, BucketNode node, PhysicalNode to, String token) {
        Random random = new Random();
        int index = random.nextInt(node.getPhysicalNodes().size());
        requestTransfer(node,
                lookupTable.getPhysicalNodeMap().get(node.getPhysicalNodes().get(index)),
                to,
                token);
        node.getPhysicalNodes().remove(index);
        node.getPhysicalNodes().add(to.getId());
    }

    public void onTableExpand(LookupTable table, int size) {
        SimpleLog.i("Expanding table...");

        int originalSize = table.getTable().length;
        if (size <= originalSize || size % originalSize != 0) {
            SimpleLog.i("Failed to expand table. Desired size is smaller than the current, or is not multiplier of current");
            return;
        }
        table.expandTable();

        for (int i = 0; i< originalSize; i++) {
            table.getTable()[originalSize + i].getPhysicalNodes().addAll(table.getTable()[i].getPhysicalNodes());
        }

        SimpleLog.i("Table expanded. No file transfer needed");
    }

    public void onTableShrink(LookupTable table) {
        SimpleLog.i("Shrinking table...");

        if (table.getTable().length  / 2 < Config.getInstance().getDefaultNumberOfHashSlots()) {
            SimpleLog.i("Table cannot be shrunk anymore");
            return;
        }
        int newSize = table.getTable().length / 2;

        String token = UUID.randomUUID().toString();
        for (int i = 0; i< newSize; i++) {
            for (String nodeId : table.getTable()[newSize + i].getPhysicalNodes()) {
                if (table.getTable()[i].getPhysicalNodes().contains(nodeId)) continue;

                for (String targetId : table.getTable()[i].getPhysicalNodes()) {
                    requestTransfer(table.getTable()[newSize + i],
                            table.getPhysicalNodeMap().get(nodeId),
                            table.getPhysicalNodeMap().get(targetId),
                            token);
                }
            }
        }

        table.shrinkTable();
        SimpleLog.i("Table shrank.");
    }

    private void requestTransfer(BucketNode node, PhysicalNode fromNode, PhysicalNode toNode, String token) {
        SimpleLog.i("Request to transfer hash bucket [" + node.getHash() + "] from " + fromNode.toString() + " to " + toNode.toString());
        FileTransferManager.getInstance().setTransferToken(token);
        FileTransferManager.getInstance().requestTransfer(node.getHash(), fromNode, toNode);
    }

    private void requestTransfer(List<Integer> nodes, PhysicalNode fromNode, PhysicalNode toNode, String token) {
        SimpleLog.i("Request to transfer hash bucket [" + nodes + "] from " + fromNode.toString() + " to " + toNode.toString());
        FileTransferManager.getInstance().setTransferToken(token);
        FileTransferManager.getInstance().requestTransfer(nodes, fromNode, toNode);
    }

    private void requestReplication(BucketNode node, PhysicalNode fromNode, PhysicalNode toNode) {
        SimpleLog.i("Copy hash bucket [" + node.getHash() + "] from " + fromNode.toString() + " to " + toNode.toString());
        FileTransferManager.getInstance().requestCopy(node.getHash(), fromNode, toNode);
    }

    private void requestReplication(List<Integer> nodes, PhysicalNode fromNode, PhysicalNode toNode) {
        SimpleLog.i("Copy hash bucket [" + nodes + "] from " + fromNode.toString() + " to " + toNode.toString());
        FileTransferManager.getInstance().requestCopy(nodes, fromNode, toNode);
    }
}
