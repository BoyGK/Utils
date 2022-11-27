package com.nullpt.utils.sockets;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class SelectorServer {

    private static Selector selector;
    private final int port;
    private final ExecutorService executorService;

    private final Map<Integer, SocketChannel> mSocketChannels = new LinkedHashMap<>();
    private SelectorIO mSelectorIO = null;

    static {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SelectorServer(int port, ExecutorService executorService) {
        this.port = port;
        this.executorService = executorService;
    }

    public void setSelectorIO(SelectorIO mSelectorIO) {
        this.mSelectorIO = mSelectorIO;
    }

    public void accept() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.bind(new InetSocketAddress(port));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
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
                if (key.isAcceptable()) {
                    accept(key);
                } else if (key.isWritable()) {
                    write(key);
                } else if (key.isReadable()) {
                    receive(key);
                }
            }
        }
    }

    public void send(int code, byte[] data) {
        SocketChannel socketChannel = mSocketChannels.get(code);
        if (socketChannel != null) {
            try {
                socketChannel.write(ByteBuffer.wrap(data));
            } catch (IOException e) {
                /*ignore*/
            }
        }
    }

    public void close(int code) {
        Set<SelectionKey> keys = selector.selectedKeys();
        for (SelectionKey key : keys) {
            SocketChannel channel = (SocketChannel) key.channel();
            if (channel.hashCode() == code) {
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
        }
    }

    public void stop() {
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

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        mSocketChannels.put(socketChannel.hashCode(), socketChannel);
        if (mSelectorIO != null) {
            mSelectorIO.accept(socketChannel.hashCode());
        }
    }

    private void write(SelectionKey key) {
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
