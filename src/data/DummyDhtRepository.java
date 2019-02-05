package data;

import commonmodels.Queueable;
import loadmanagement.LoadInfo;
import statmanagement.StatInfo;
import util.Config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.*;


public class DummyDhtRepository {
    private static final String TABLE_STAT_INFO = "statinfo";
    private static final String TABLE_LOAD_INFO = "loadinfo";
    private static final String TABLE_HISTORICAL_LOAD_INFO = "historicalloadinfo";

    private Connector connector;

    private Connection session;

    private ExecutorService executor;

    private ScheduledExecutorService timer;

    private ScheduledFuture task;

    private Queue<Queueable> queue;

    private TimerTask disconnectTask;

    private static volatile DummyDhtRepository instance = null;

    private static final long TIME_TO_DISCONNECT = 5;

    private DummyDhtRepository() {
        connector = new Connector();
        connector.setServer(Config.getInstance().getDataServer());
        executor = Executors.newSingleThreadExecutor();
        timer = Executors.newSingleThreadScheduledExecutor();
        queue = new LinkedList<Queueable>(){
            @Override
            public boolean add(Queueable queueable) {
                boolean result = super.add(queueable);
                process();

                return result;
            }
        };
        disconnectTask = new TimerTask() {
            @Override
            public void run() {
                close();
            }
        };
    }

    public static DummyDhtRepository getInstance() {
        if (instance == null) {
            synchronized(DummyDhtRepository.class) {
                if (instance == null) {
                    instance = new DummyDhtRepository();
                }
            }
        }

        return instance;
    }

    public void process() {
        executor.execute(this::consume);
    }

    private synchronized void consume() {
        while (!queue.isEmpty()) {
            open();
            Queueable queueable = queue.poll();
            if (queueable instanceof StatInfo)
                insertStatInfo((StatInfo) queueable);
            else if (queueable instanceof LoadInfo)
                insertLoadInfo((LoadInfo) queueable);
        }
    }

    public void put(Queueable queueable) {
        queue.add(queueable);
    }

    private void insertLoadInfo(LoadInfo info) {
        try {
            String table = info.isConsolidated() ? TABLE_HISTORICAL_LOAD_INFO : TABLE_LOAD_INFO;
            PreparedStatement statement = session.prepareStatement(
                    "INSERT INTO " + table + " (report_time, node_id, file_load, number_of_hits, number_of_lock_conflicts, number_of_miss, read_load, size_of_files, write_load) " +
                            "VALUES (?, ?, ?, ? , ? , ?, ?, ?, ?)");
            statement.setTimestamp(1, new Timestamp(info.getReportTime()));
            statement.setString(2, info.getNodeId());
            statement.setLong(3, info.getFileLoad());
            statement.setLong(4, info.getNumberOfHits());
            statement.setLong(5, info.getNumberOfLockConflicts());
            statement.setLong(6, info.getNumberOfMiss());
            statement.setLong(7, info.getReadLoad());
            statement.setLong(8, info.getSizeOfFiles());
            statement.setLong(9, info.getWriteLoad());

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void insertStatInfo(StatInfo info) {
        try {
            PreparedStatement statement = session.prepareStatement(
                    "INSERT INTO " + TABLE_STAT_INFO + " (entry_token, start_time, header, elapsed, end_time, type) " +
                            "VALUES (?, ?, ?, ? , ? , ?)");
            statement.setString(1, info.getToken());
            statement.setTimestamp(2, new Timestamp(info.getStartTime()));
            statement.setString(3, info.getHeader());
            statement.setLong(4, info.getElapsed());
            statement.setTimestamp(5, new Timestamp(info.getEndTime()));
            statement.setString(6, info.getType());

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void open() {
        if (task != null)
            task.cancel(false);

        if (session == null || !connector.isConnected()) {
            session = connector.reconnect();
        }

        task = timer.schedule(disconnectTask, TIME_TO_DISCONNECT, TimeUnit.SECONDS);
    }

    public void close() {
        connector.close();
        session = null;
    }
}
