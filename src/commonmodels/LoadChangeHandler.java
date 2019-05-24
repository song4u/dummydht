package commonmodels;

import commonmodels.transport.Request;
import loadmanagement.LoadInfo;

import java.util.List;

public interface LoadChangeHandler {

    List<Request> generateRequestBasedOnLoad(List<LoadInfo> globalLoad, LoadInfo loadInfo, long lowerBound, long upperBound);

    void optimize(List<Request> requests);

}
