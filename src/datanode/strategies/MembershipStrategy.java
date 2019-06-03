package datanode.strategies;

import commands.DaemonCommand;
import commonmodels.DataNode;
import commonmodels.LoadInfoReportHandler;
import commonmodels.transport.InvalidRequestException;
import commonmodels.transport.Request;
import commonmodels.transport.Response;
import socket.SocketClient;
import util.SimpleLog;

import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MembershipStrategy implements LoadInfoReportHandler {

    protected DataNode dataNode;

    protected SocketClient socketClient;

    public MembershipStrategy(DataNode dataNode) {
        this.dataNode = dataNode;
        this.socketClient = new SocketClient();
    }

    public abstract Response getMembersStatus();

    public void onNodeStarted() throws InterruptedException, UnknownHostException, URISyntaxException, InvalidRequestException {
        // in case bootstrap takes a long time to response, wrap it with a thread
        new Thread(this::bootstrap).start();
    }

    public void onNodeStopped() {
        // stub
    }

    private void bootstrap() {
        AtomicBoolean fetched = new AtomicBoolean(false);
        SocketClient.ServerCallBack callBack = new SocketClient.ServerCallBack() {
            @Override
            public void onResponse(Request request, Response o) {
                SimpleLog.i(o);

                if (o.getStatus() == Response.STATUS_FAILED) {
                    onFailure(request, o.getMessage());
                }
                else {
                    dataNode.updateTable(o.getAttachment());
                    fetched.set(true);
                }
            }

            @Override
            public void onFailure(Request request, String error) {
                SimpleLog.i(error);
            }
        };

        for (String seed : dataNode.getSeeds()) {
            if (!seed.equals(dataNode.getAddress()) && !seed.equals(dataNode.getLocalAddress())) {
                Request request = new Request().withHeader(DaemonCommand.FETCH.name())
                                                .withFollowup(dataNode.getAddress());
                socketClient.send(seed, request, callBack);
            }
            if (fetched.get()) break;
        }

        if (!fetched.get()) {
            SimpleLog.i("Creating table");
            dataNode.createTable();
        }
    }
}
