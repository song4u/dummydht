package elastic;

import loadmanagement.LoadInfo;

import java.util.List;

public class ElasticDecentLoadChangeHandler extends ElasticLoadChangeHandler {
    public ElasticDecentLoadChangeHandler(LookupTable table) {
        super(table);
    }

    @Override
    public long computeTargetLoad(List<LoadInfo> loadInfoList, LoadInfo loadInfo, long lowerBound, long upperBound) {
        if (loadInfoList == null || loadInfoList.size() < 1) return lowerBound;
        else return loadInfo.getLoad() - (upperBound - loadInfoList.get(0).getLoad());
    }
}
