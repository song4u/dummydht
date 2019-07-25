package socket;

import commonmodels.transport.Request;
import commonmodels.transport.Response;
import statmanagement.StatInfoManager;
import util.ObjectConverter;
import util.SimpleLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Queue;

public class ClientReadWriteHandler implements Runnable, Attachable {
    private final SocketChannel socketChannel;
    private final SocketClient.ServerCallBack callBack;
    private final Request data;
    private final Queue<Attachable> attachments;

    private static final int READ_BUF_SIZE = 32 * 1024;

    private SelectionKey selectionKey;
    private ByteBuffer[] _readBuf;
    private ByteBuffer[] _writeBuf;
    private ByteArrayOutputStream bos = new ByteArrayOutputStream();
    private int size;

    public ClientReadWriteHandler(SocketChannel socketChannel, Request data, SocketClient.ServerCallBack callBack, Queue<Attachable> attachments) throws IOException {
        this.socketChannel = socketChannel;
        this.socketChannel.configureBlocking(false);
        this.data = data;
        this.callBack = callBack;
        this.attachments = attachments;

        this._writeBuf = new ByteBuffer[2];
        this._writeBuf[1] = ObjectConverter.getByteBuffer(data);
        this._writeBuf[0] = ByteBuffer.allocate(Integer.BYTES);
        this._writeBuf[0].putInt(_writeBuf[1].remaining());
        this._writeBuf[0].flip();
        // SimpleLog.v("Client write buffer: position " + _writeBuf[1].position() + ", remaining " + _writeBuf[1].remaining());
        this._readBuf = new ByteBuffer[2];
        this._readBuf[1] = ByteBuffer.allocate(READ_BUF_SIZE);
        this._readBuf[0] = ByteBuffer.allocate(Integer.BYTES);

        this.size = Integer.MIN_VALUE;
    }

    @Override
    public void run() {
        try {
            if (!this.selectionKey.isValid() || !this.socketChannel.isOpen()) return;
            if (this.selectionKey.isReadable()) {
                read();
            }
            else if (this.selectionKey.isWritable()) {
                write();
            }
        }
        catch (IOException ex) {
            attachments.add(new Recycler(selectionKey));
            SimpleLog.e(ex);
        }
    }

    private void process() {
        byte[] byteArray = bos.toByteArray();
        int respSize = byteArray.length;
        if (respSize == 0) return;

        Object o = ObjectConverter.getObject(byteArray);
        if (o instanceof Response) {
            Response resp = (Response) o;
            StatInfoManager.getInstance().statResponse(data, resp, respSize);
            callBack.onResponse(data, resp);
        }
        else {
            // SimpleLog.i("[" + socketChannel.getRemoteAddress() + "] Client: process bytes " + respSize + ", object is " + o);
            StatInfoManager.getInstance().statRoundTripFailure(data);
            callBack.onFailure(data, String.valueOf(o));
        }
    }

    private synchronized void read() throws IOException {
        if (this.socketChannel.read(_readBuf) == -1){
            this.selectionKey.cancel();
            this.socketChannel.close();
            return;
        }

        int numBytes;
        _readBuf[1].flip();

        if (size == Integer.MIN_VALUE) {
            _readBuf[0].flip();
            size = _readBuf[0].getInt();
        }
        numBytes = _readBuf[1].remaining();

        // SimpleLog.v("[" + socketChannel.getRemoteAddress() + "] Client: read bytes " + numBytes);

        if (size < 0) {
            reset();
            return;
        }
        if (numBytes >= size) {
            // object fully arrived
            bos.write(ObjectConverter.getBytes(_readBuf[1], size));
            size = 0;
        }
        else {
            // object partially arrived, need to read again
            bos.write(ObjectConverter.getBytes(_readBuf[1], numBytes));
            size -= numBytes;
        }

        if (size <= 0 && size != Integer.MIN_VALUE) {
            attachments.add(new Recycler(selectionKey));
            process();
            reset();
        }
        else {
            _readBuf[1].clear();
        }
    }

    private void write() throws IOException {
        this.socketChannel.write(_writeBuf);

        if (!_writeBuf[0].hasRemaining() && !_writeBuf[1].hasRemaining()) {
            this.selectionKey.interestOps(SelectionKey.OP_READ);
            this.selectionKey.selector().wakeup();
        }
    }

    private void reset() {
        size = Integer.MIN_VALUE;
        _readBuf[0].clear();
        _readBuf[1].clear();
        _writeBuf[0].clear();
        _writeBuf[1].clear();
        bos.reset();
    }

    @Override
    public void attach(Selector selector) throws IOException {
        try {
            this.selectionKey = this.socketChannel.register(selector, SelectionKey.OP_WRITE);
            this.selectionKey.attach(this);
        }
        catch (CancelledKeyException ignored) {}
    }
}