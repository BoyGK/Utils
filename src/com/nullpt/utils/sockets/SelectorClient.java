package com.nullpt.utils.sockets;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * nio client
 */
public class SelectorClient {

    private static Selector selector;
    private final String ip;
    private final int port;
    private final ExecutorService executorService;

    private SocketChannel mSocketChannel;
    private SelectorIO mSelectorIO = null;

    static {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SelectorClient(String ip, int port, ExecutorService executorService) {
        this.ip = ip;
        this.port = port;
        this.executorService = executorService;
    }

    public void setSelectorIO(SelectorIO mSelectorIO) {
        this.mSelectorIO = mSelectorIO;
    }

    public void connect() {
        try {
            SocketChannel socketChannel = SocketChannel.open();
            SocketAddress remote = new InetSocketAddress(ip, port);
            socketChannel.configureBlocking(false);
            socketChannel.connect(remote);
            socketChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        } catch (Exception e) {
            /*ignore*/
        }

        executorService.execute(() -> {
            try {
                select();
            } catch (IOException e) {
                /*ignore*/
            }
        });
    }

    private void select() throws IOException {
        while (selector.select() > 0) {
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> it = keys.iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();
                if (key.isConnectable()) {
                    connect(key);
                } else if (key.isWritable()) {
                    write(key);
                } else if (key.isReadable()) {
                    receive(key);
                }
            }
        }
    }

    public void send(byte[] data) {
        if (mSocketChannel != null) {
            try {
                mSocketChannel.write(ByteBuffer.wrap(data));
            } catch (IOException e) {
                /*ignore*/
            }
        }
    }

    public void close() {
        Set<SelectionKey> keys = selector.selectedKeys();
        for (SelectionKey key : keys) {
            SocketChannel channel = (SocketChannel) key.channel();
            try {
                channel.shutdownInput();
            } catch (IOException e) {
                /*ignore*/
            }
            try {
                channel.shutdownOutput();
            } catch (IOException e) {
                /*ignore*/
            }
            try {
                channel.close();
            } catch (IOException e) {
                /*ignore*/
            }
        }
        try {
            selector.close();
        } catch (IOException e) {
            /*ignore*/
        }
    }

    private void connect(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        channel.finishConnect();
        mSocketChannel = channel;
        if (mSelectorIO != null) {
            mSelectorIO.connect(channel.hashCode());
        }
    }

    private void write(SelectionKey key) throws IOException {
        key.interestOps(SelectionKey.OP_READ);
    }

    private void receive(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        channel.read(buffer);
        buffer.flip();
        if (mSelectorIO != null) {
            mSelectorIO.receive(channel.hashCode(), buffer.array());
        }
    }
}
