package loadmanagement;

import filemanagement.LocalFileManager;
import util.Config;

public class LoadInfoManager {

    private LoadInfo loadInfo;

    private LoadInfoReporter reporter;

    private static volatile LoadInfoManager instance = null;

    private static String nodeId;

    private LoadInfoManager() {
        loadInfo = new LoadInfo();
        loadInfo.setNodeId(nodeId);
        loadInfo.setReadFactor(Config.getInstance().getReadFactor());
        reporter = new LoadInfoReporter(this);
    }

    public static LoadInfoManager getInstance() {
        if (instance == null) {
            synchronized(LoadInfoManager.class) {
                if (instance == null) {
                    instance = new LoadInfoManager();
                }
            }
        }

        return instance;
    }

    public static void with(String id) {
        nodeId =id;
        getInstance().reporter.start();
    }

    public LoadInfo getLoadInfo() {
        return LocalFileManager.getInstance().updateLoadInfo(loadInfo);
    }

    public void incrementNumberOfRead() {
        loadInfo.incrementNumberOfRead();
    }

    public void increaseWriteLoad(long write) {
        loadInfo.increaseWriteLoad(write);
    }

    public void incrementNumberOfHits() {
        loadInfo.incrementNumberOfHits();
    }

    public void incrementNumberOfMiss() {
        loadInfo.incrementNumberOfMiss();
    }

    public void incrementNumberOfLockConflicts() {
        loadInfo.incrementNumberOfLockConflicts();
    }
}
