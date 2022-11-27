package com.nullpt.utils.sockets;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * TCP数据包协议
 * [content-length:4][data]
 */
public class TCPPackageProtocol {

    /**
     * 数据长度位
     */
    private static final int CONTENT_LENGTH = 4;
    /**
     * 单次读取数据位长度
     */
    private static final int READ_LENGTH = 1024;
    /**
     * 数据长度
     */
    private final byte[] lengthBytes = new byte[CONTENT_LENGTH];
    /**
     * 读取长度
     */
    private final byte[] readBytes = new byte[READ_LENGTH];

    /**
     * 数据
     */
    private byte[] data = null;

    /**
     * 发送数据
     *
     * @param outputStream os
     * @param data         数据
     */
    public void send(OutputStream outputStream, byte[] data) throws IOException {
        outputStream.write(Utils.int2bytes(data.length));
        outputStream.write(data);
        outputStream.flush();
    }

    /**
     * 接收数据
     *
     * @param inputStream is
     * @param callback    数据回调
     */
    public void receive(InputStream inputStream, ReceiveCompleteCallback callback) throws IOException {
        while (!Thread.interrupted()) {
            int ignoreLength = inputStream.read(lengthBytes, 0, CONTENT_LENGTH);
            /*这个长度一般来说一定能一次读回来*/
            assert ignoreLength == CONTENT_LENGTH;

            int length = Utils.bytes2int(lengthBytes);

            if (data == null || data.length < length) {
                data = new byte[length];
            }

            int dataLength = 0;
            while (dataLength < length) {
                int readLength;
                if (dataLength + READ_LENGTH < length) {
                    readLength = READ_LENGTH;
                } else {
                    readLength = length - dataLength;
                }
                int len = inputStream.read(readBytes, 0, readLength);
                System.arraycopy(readBytes, 0, data, dataLength, len);
                dataLength += len;
            }
            callback.complete(data, dataLength);
        }
    }

    /**
     * 完整数据回调接口
     */
    public interface ReceiveCompleteCallback {
        /**
         * 完整数据回调
         *
         * @param data   数据，可能是新的，可能是复用的
         * @param length 数据位长度
         */
        void complete(byte[] data, int length);
    }
}
