package entries;

import commands.DaemonCommand;
import commands.RingCommand;
import commonmodels.Daemon;
import commonmodels.PhysicalNode;
import commonmodels.ReadWriteCallBack;
import commonmodels.transport.InvalidRequestException;
import commonmodels.transport.Request;
import commonmodels.transport.Response;
import datanode.DataNodeServer;
import filemanagement.FileBucket;
import filemanagement.FileTransferManager;
import loadmanagement.LoadInfoManager;
import org.apache.commons.lang3.StringUtils;
import socket.SocketClient;
import socket.SocketServer;
import util.Config;
import util.SimpleLog;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataNodeDaemon implements Daemon, ReadWriteCallBack {

    private SocketServer socketServer;

    private SocketClient socketClient = new SocketClient();

    private DataNodeServer dataNodeServer;

    private String ip;

    private int port;

    private ExecutorService executor = Executors.newFixedThreadPool(2);

    public static void main(String[] args){
        if (args.length > 1)
        {
            System.err.println ("Usage: DataNodeDaemon [daemon port]");
            return;
        }

        int daemonPort = 6000;
        if (args.length > 0)
        {
            try
            {
                daemonPort = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException e)
            {
                System.err.println ("Invalid daemon port: " + e);
                return;
            }
            if (daemonPort <= 0 || daemonPort > 65535)
            {
                System.err.println ("Invalid daemon port");
                return;
            }
        }

        try {
            DataNodeDaemon daemon = DataNodeDaemon.newInstance(getAddress(), daemonPort);
            SimpleLog.with(daemon.ip, daemon.port);
            SimpleLog.i("Daemon: " + daemonPort + " started");
            daemon.exec();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static volatile DataNodeDaemon instance = null;

    public static DataNodeDaemon getInstance() {
        return instance;
    }

    public static DataNodeDaemon newInstance(String ip, int port) {
        instance = new DataNodeDaemon(ip, port);
        return getInstance();
    }

    public static DataNodeDaemon newInstance(String address) {
        String[] temp = address.split(":");
        instance = new DataNodeDaemon(temp[0], Integer.valueOf(temp[1]));
        return getInstance();
    }

    private DataNodeDaemon(String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.socketServer = new SocketServer(this.port, this);
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public void setSocketEventHandler(SocketServer.EventHandler handler) {
        socketServer.setEventHandler(handler);
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    @Override
    public void exec() throws Exception {
        socketServer.start();
    }

    @Override
    public void initDataNodeServer() throws Exception {
        dataNodeServer = new DataNodeServer(ip, port);
    }

    @Override
    public void initSubscriptions() {
        dataNodeServer.setReadWriteCallBack(this);
        FileTransferManager.getInstance().subscribe(this);
        if (Config.getInstance().getMode().equals(Config.MODE_DISTRIBUTED)) {
            // in decentralized mode, node are only responsible for transferring files
            // that they are involved
            // if it happens that they receive request to transfer files between other
            // nodes, the following settings make the node only update the table,
            // thus avoiding unnecessary file transfer
            FileTransferManager.getInstance().setPolicy(FileTransferManager.FileTransferPolicy.SenderOrReceiver);
            FileTransferManager.getInstance().setMySelf(dataNodeServer.getDataNode().getAddress());
        }
        LoadInfoManager.with(dataNodeServer.getDataNode().getAddress());
        LoadInfoManager.getInstance().setLoadInfoReportHandler(dataNodeServer.getMembershipStrategy());
    }

    @Override
    public void startDataNodeServer() throws Exception {
        if (dataNodeServer == null) {
            initDataNodeServer();
            initSubscriptions();
            dataNodeServer.start();
        }
        else {
            throw new Exception("Data node is already started");
        }
    }

    @Override
    public void stopDataNodeServer() throws Exception {
        if (dataNodeServer == null) {
            throw new Exception("Data node is not started yet");
        }
        else {
            dataNodeServer.stop();
            dataNodeServer = null;
            FileTransferManager.getInstance().unsubscribe(this);
        }
    }

    @Override
    public DataNodeServer getDataNodeServer() {
        return dataNodeServer;
    }

    private static String getAddress() {
        try(final DatagramSocket socket = new DatagramSocket()){
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (UnknownHostException | SocketException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void onTransferring(List<Integer> buckets, PhysicalNode from, PhysicalNode toNode) {
        // send request to the "from" node, ask "from" to transfer the buckets
        Request request = new Request()
                .withHeader(DaemonCommand.TRANSFER.name())
                .withSender(from.getFullAddress())
                .withReceiver(toNode.getFullAddress())
                .withAttachment(StringUtils.join(buckets, ','));
        send(from.getAddress(), from.getPort(), request, this);
    }

    @Override
    public void onReplicating(List<Integer> buckets, PhysicalNode from, PhysicalNode toNode) {
        // send request to the "from" node, ask "from" to copy the buckets
        Request request = new Request()
                .withHeader(DaemonCommand.COPY.name())
                .withSender(from.getFullAddress())
                .withReceiver(toNode.getFullAddress())
                .withAttachment(StringUtils.join(buckets, ','));
        send(from.getAddress(), from.getPort(), request, this);
    }

    @Override
    public void onTransmitted(List<FileBucket> buckets, PhysicalNode from, PhysicalNode toNode) {
        // send request to the "toNode" node, ask "toNode" to receive the buckets
        Request request = new Request()
                .withHeader(DaemonCommand.RECEIVED.name())
                .withSender(from.getFullAddress())
                .withReceiver(toNode.getFullAddress())
                .withLargeAttachment(buckets);

        send(toNode.getAddress(), toNode.getPort(), request, this);
    }

    @Override
    public void onReceived(AsynchronousSocketChannel out, Request o, SocketServer.EventResponsor responsor) throws Exception {
        Response response = processCommonCommand(o);
        if (response.getStatus() == Response.STATUS_INVALID_REQUEST)
            response = processDataNodeCommand(o);

        responsor.reply(out, response);
    }

    @Override
    public void onBound() {

    }

    @Override
    public Response processCommonCommand(Request o) {
        try {
            DaemonCommand command = DaemonCommand.valueOf(o.getHeader());
            return command.execute(o);
        }
        catch (IllegalArgumentException e) {
            return new Response(o).withStatus(Response.STATUS_INVALID_REQUEST)
                    .withMessage(e.getMessage());
        }
    }

    @Override
    public Response processDataNodeCommand(Request o) {
        try {
            if (dataNodeServer == null)
                return new Response(o).withStatus(Response.STATUS_FAILED)
                        .withMessage("Node not started");

            return dataNodeServer.processCommand(o);
        }
        catch (InvalidRequestException e) {
            return new Response(o).withStatus(Response.STATUS_FAILED)
                    .withMessage(e.getMessage());
        }
    }

    @Override
    public void send(String address, int port, Request request, SocketClient.ServerCallBack callBack) {
        socketClient.send(address, port, request, callBack);
    }

    @Override
    public void send(String address, Request request, SocketClient.ServerCallBack callBack) {
        socketClient.send(address, request, callBack);
    }

    @Override
    public void onResponse(Request request, Response o) {
        SimpleLog.i(o);
    }

    @Override
    public void onFailure(Request request, String error) {
        SimpleLog.i(error);
    }

    @Override
    public void onFileWritten(String file, List<PhysicalNode> replicas) {
        // replicate file to other replicas
        executor.execute(() -> backupFile(file, replicas));
    }

    private void backupFile(String file, List<PhysicalNode> replicas) {
        Request request = new Request().withHeader(RingCommand.WRITE.name())
                .withEpoch(-1)
                .withAttachment(file);

        for (PhysicalNode node : replicas) {
            if (!node.getFullAddress().equals(dataNodeServer.getDataNode().getAddress()))
                send(node.getAddress(), node.getPort(), request, this);
        }
    }
}
