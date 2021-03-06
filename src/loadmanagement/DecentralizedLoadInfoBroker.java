package loadmanagement;

import java.util.ArrayList;
import java.util.List;

public class DecentralizedLoadInfoBroker extends LoadInfoBroker {

    private static volatile DecentralizedLoadInfoBroker instance = null;

    private DecentralizedLoadInfoBroker() {
    }

    public static DecentralizedLoadInfoBroker getInstance() {
        if (instance == null) {
            synchronized(DecentralizedLoadInfoBroker.class) {
                if (instance == null) {
                    instance = new DecentralizedLoadInfoBroker();
                }
            }
        }

        return instance;
    }

    public static void deleteInstance() {
        instance = null;
    }

    @Override
    public void update(LoadInfo loadInfo) {
        List<LoadInfo> loadInfoList = new ArrayList<>();
        loadInfoList.add(loadInfo);
        announce(loadInfoList);
    }
}
