package socket;

import util.ObjectConverter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class SocketClient {

    private AsynchronousSocketChannel asynchronousSocketChannel = null;

    public SocketClient() {
    }

    public void sendAsync(int port, Object data, ServerCallBack callBack) {
        sendAsync(new InetSocketAddress("localhost", port), data, callBack);
    }

    public void sendAsync(String address, int port, Object data, ServerCallBack callBack) {
        sendAsync(new InetSocketAddress(address, port), data, callBack);
    }

    public void sendAsync(String address, Object data, ServerCallBack callBack) {
        String[] address1 = address.split(":");
        sendAsync(new InetSocketAddress(address1[0], Integer.valueOf(address1[1])), data, callBack);
    }

    private void sendAsync(InetSocketAddress inetSocketAddress, Object data, ServerCallBack callBack) {
        CountDownLatch requestLatch = new CountDownLatch(1);

        try {
            if (asynchronousSocketChannel == null)
                asynchronousSocketChannel = initAsynchronousSocketChannel();

            if (asynchronousSocketChannel != null) {
                asynchronousSocketChannel.connect(inetSocketAddress, null, new CompletionHandler<Void, Void>() {

                    @Override
                    public void completed(Void result, Void attachment) {
                        onServerConnectedOmitResponse(data, callBack);
                        requestLatch.countDown();
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment) {
                        callBack.onFailure("Connection cannot be established!");
                        requestLatch.countDown();
                    }
                });

                requestLatch.await();
            } else {
                callBack.onFailure("The asynchronous socket channel cannot be opened!");
            }

        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
            callBack.onFailure("An error occurred");
        } catch (AlreadyConnectedException e) {
            onServerConnectedOmitResponse(data, callBack);
        }
        finally {
            requestLatch.countDown();
        }
    }

    public void send(int port, Object data, ServerCallBack callBack) {
        send(new InetSocketAddress("localhost", port), data, callBack);
    }

    public void send(String address, int port, Object data, ServerCallBack callBack) {
        send(new InetSocketAddress(address, port), data, callBack);
    }

    public void send(String address, Object data, ServerCallBack callBack) {
        String[] address1 = address.split(":");
        send(new InetSocketAddress(address1[0], Integer.valueOf(address1[1])), data, callBack);
    }

    private void send(InetSocketAddress inetSocketAddress, Object data, ServerCallBack callBack) {
        try {
            if (asynchronousSocketChannel == null)
                asynchronousSocketChannel = initAsynchronousSocketChannel();

            if (asynchronousSocketChannel != null) {
                Void connect = asynchronousSocketChannel.connect(inetSocketAddress).get();

                if (connect == null)
                    onServerConnected(data, callBack);
                else
                    callBack.onFailure("Connection cannot be established!");
            }
            else {
                callBack.onFailure("The asynchronous socket channel cannot be opened!");
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
            callBack.onFailure("An error occurred");
        }
    }

    private AsynchronousSocketChannel initAsynchronousSocketChannel() throws IOException {
        AsynchronousSocketChannel asynchronousSocketChannel = AsynchronousSocketChannel.open();

        if (asynchronousSocketChannel.isOpen()) {
            asynchronousSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 128 * 1024);
            asynchronousSocketChannel.setOption(StandardSocketOptions.SO_SNDBUF, 128 * 1024);
            asynchronousSocketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);

        }

        return asynchronousSocketChannel;
    }

    private void onServerConnectedOmitResponse(Object data, ServerCallBack callBack) {
        boolean success = false;
        Object o = "Data sent";
        String message = "Unknown server connection error";

        try {
            final ByteBuffer sendBuffer = ObjectConverter.getByteBuffer(data);
            // transmitting data
            asynchronousSocketChannel.write(sendBuffer).get();
            success = true;
        } catch (IOException | InterruptedException | ExecutionException ex) {
            System.err.println(ex);
            message = ex.getMessage();
        } finally {
            try {
                asynchronousSocketChannel.close();

                if (success)
                    callBack.onResponse(o);
                else
                    callBack.onFailure(message);

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void onServerConnected(Object data, ServerCallBack callBack) {
        boolean success = false;
        Object o = null;
        String message = "Unknown server connection error";

        try {
            System.out.println("Successfully connected at: " + asynchronousSocketChannel.getRemoteAddress());

            final ByteBuffer sendBuffer = ObjectConverter.getByteBuffer(data);
            final ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
            // transmitting data
            asynchronousSocketChannel.write(sendBuffer).get();

            // read response
            asynchronousSocketChannel.read(buffer).get();
            buffer.flip();
            o = ObjectConverter.getObject(buffer);
            success = true;
        } catch (IOException | InterruptedException | ExecutionException ex) {
            System.err.println(ex);
            message = ex.getMessage();
        } finally {
            try {
                asynchronousSocketChannel.close();

                if (success)
                    callBack.onResponse(o);
                else
                    callBack.onFailure(message);

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public interface ServerCallBack {
        void onResponse(Object o);
        void onFailure(String error);
    }
}
