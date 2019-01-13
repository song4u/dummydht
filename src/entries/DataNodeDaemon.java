package entries;

import commands.DaemonCommand;
import commonmodels.Daemon;
import commonmodels.PhysicalNode;
import commonmodels.transport.InvalidRequestException;
import commonmodels.transport.Request;
import commonmodels.transport.Response;
import datanode.DataNodeServer;
import filemanagement.FileBucket;
import filemanagement.FileTransferManager;
import org.apache.commons.lang3.StringUtils;
import socket.SocketClient;
import socket.SocketServer;
import util.ObjectConverter;
import util.SimpleLog;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;

public class DataNodeDaemon implements Daemon {

    private SocketServer socketServer;

    private SocketClient socketClient = new SocketClient();

    private DataNodeServer dataNodeServer;

    private String ip;

    private int port;

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

    @Override
    public void exec() throws Exception {
        socketServer.start();
    }

    @Override
    public void startDataNodeServer() throws Exception {
        if (dataNodeServer == null) {
            dataNodeServer = new DataNodeServer(ip, port);
            dataNodeServer.start();
            FileTransferManager.getInstance().subscribe(this);
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
        Request request = new Request()
                .withHeader(DaemonCommand.TRANSFER.name())
                .withSender(from.getFullAddress())
                .withReceiver(toNode.getFullAddress())
                .withAttachment(StringUtils.join(buckets, ','));
        send(from.getAddress(), from.getPort(), request, this);
    }

    @Override
    public void onReplicating(List<Integer> buckets, PhysicalNode from, PhysicalNode toNode) {
        Request request = new Request()
                .withHeader(DaemonCommand.COPY.name())
                .withSender(from.getFullAddress())
                .withReceiver(toNode.getFullAddress())
                .withAttachment(StringUtils.join(buckets, ','));
        send(from.getAddress(), from.getPort(), request, this);
    }

    @Override
    public void onTransmitted(List<FileBucket> buckets, PhysicalNode from, PhysicalNode toNode) {

    }

    @Override
    public void onReceived(AsynchronousSocketChannel out, Request o) throws Exception {
        Response response = processCommonCommand(o);
        if (response.getStatus() == Response.STATUS_INVALID_REQUEST)
            response = processDataNodeCommand(o);

        ByteBuffer buffer = ObjectConverter.getByteBuffer(response);
        out.write(buffer).get();
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
    public void onResponse(Response o) {
        SimpleLog.i(String.valueOf(o));
    }

    @Override
    public void onFailure(String error) {
        SimpleLog.i(error);
    }
}
