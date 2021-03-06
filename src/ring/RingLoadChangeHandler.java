package ring;

import commands.RingCommand;
import commonmodels.Indexable;
import commonmodels.LoadChangeHandler;
import commonmodels.PhysicalNode;
import commonmodels.transport.Request;
import filemanagement.FileBucket;
import loadmanagement.LoadInfo;
import org.apache.commons.lang3.StringUtils;
import util.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RingLoadChangeHandler implements LoadChangeHandler {

    protected final LookupTable table;

    protected float readOverhead;

    protected float writeOverhead;

    protected long interval;

    public RingLoadChangeHandler(LookupTable table) {
        this.table = table;
        readOverhead = Config.getInstance().getReadOverhead();
        writeOverhead = Config.getInstance().getWriteOverhead();
        interval = Config.getInstance().getLoadInfoReportInterval() / 1000;
    }

    @Override
    public List<Request> generateRequestBasedOnLoad(List<LoadInfo> globalLoad, LoadInfo loadInfo, long lowerBound, long upperBound) {
        PhysicalNode node = new PhysicalNode(loadInfo.getNodeId());
        PhysicalNode pnode = table.getPhysicalNodeMap().get(node.getId());
        Solution bestSolution = null;
        long target = computeTargetLoad(globalLoad, loadInfo, lowerBound, upperBound);

        if (pnode == null || pnode.getVirtualNodes() == null) return null;
        for (int i = 0; i < pnode.getVirtualNodes().size(); i++) {
            Indexable vnode = pnode.getVirtualNodes().get(i);

            if (!isEligibleToBalance(vnode, globalLoad)) continue;

            Indexable predecessor = table.getTable().pre(vnode);
            Solution solution = evaluate(loadInfo, predecessor, vnode, target);

            if (bestSolution == null || solution.getResultLoad() < bestSolution.getResultLoad())
                bestSolution = solution;
        }

        if (bestSolution == null) return null;

        List<Request> requests = new ArrayList<>();
        requests.add(generateRequestBasedOnSolution(pnode, bestSolution));
        return requests;
    }

    @Override
    public void optimize(List<Request> requests) {
        // stub
    }

    @Override
    public long computeTargetLoad(List<LoadInfo> loadInfoList, LoadInfo loadInfo, long lowerBound, long upperBound) {
        return lowerBound;
    }

    protected boolean isEligibleToBalance(Indexable curr, List<LoadInfo> loadInfoList) {
        return true;
    }

    protected Request generateRequestBasedOnSolution(PhysicalNode pnode, Solution solution) {
        List<Integer> hashList = new ArrayList<>();
        for (Indexable vnode : pnode.getVirtualNodes()) {
            if (vnode.getHash() == solution.getVnodeHash()) {
                int hf = vnode.getHash() - solution.getDelta();
                if (hf < 0) hf = Config.getInstance().getNumberOfHashSlots() + hf;
                hashList.add(hf);
            }
            else {
                hashList.add(vnode.getHash());
            }
        }

        return new Request().withHeader(RingCommand.DECREASELOAD.name())
                .withReceiver(pnode.getFullAddress())
                .withAttachments(pnode.getFullAddress(), StringUtils.join(hashList, ','));
    }

    protected Solution evaluate(LoadInfo loadInfo, Indexable predecessor, Indexable current, long target) {
        Solution solution = new Solution(loadInfo.getLoad(), current.getHash(), loadInfo.getNodeId());
        Map<Integer, FileBucket> map = loadInfo.getBucketInfoList().stream().collect(
                Collectors.toMap(FileBucket::getKey, bucket -> bucket, FileBucket::merge));

        int iterator = current.getHash();
        int start = predecessor.getHash() + 1; // the hash of predecessor needs to be excluded
        while (inRange(iterator, start, current.getHash())) {
            FileBucket bucket = map.get(iterator--);
            if (bucket != null &&
                    !solution.update(
                            bucket.getKey(),
                            bucket.getLoad(readOverhead, writeOverhead, interval),
                            target))
                break;

            if (iterator < 0) iterator = Config.getInstance().getNumberOfHashSlots() - 1;
        }

        return solution;
    }

    private boolean inRange(int bucket, int start, int end) {
        if (start > end) {
            return (bucket > start && bucket < Config.getInstance().getNumberOfHashSlots()) ||
                    (bucket >= 0 && bucket <= end);
        }
        else {
            return bucket > start && bucket <= end;
        }
    }

    protected static class Solution {
        private List<Integer> buckets;
        private long resultLoad;
        private int delta;
        private int vnodeHash;
        private String nodeId;

        public Solution(long initLoad, int vnodeHash, String nodeId) {
            buckets = new ArrayList<>();
            resultLoad = initLoad;
            delta = 0;
            this.vnodeHash = vnodeHash;
            this.nodeId = nodeId;
        }

        public List<Integer> getBuckets() {
            return buckets;
        }

        public void setBuckets(List<Integer> buckets) {
            this.buckets = buckets;
        }

        public long getResultLoad() {
            return resultLoad;
        }

        public void setResultLoad(long resultLoad) {
            this.resultLoad = resultLoad;
        }

        public int getDelta() {
            return delta;
        }

        public void setDelta(int delta) {
            this.delta = delta;
        }

        public int getVnodeHash() {
            return vnodeHash;
        }

        public void setVnodeHash(int vnodeHash) {
            this.vnodeHash = vnodeHash;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public boolean update(int bucket, long load, long target) {
            long temp = resultLoad - load;
            if (temp < target) {
                return false;
            }
            else {
                buckets.add(bucket);
                resultLoad -= load;
                delta++;
                return true;
            }
        }
    }
}
