package com.nullpt.utils.sockets;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * UDP数据包协议
 * [id-time/ms:32][range-size:4][range-index:4][range-length:4][content-length:4][data:32K]
 */
public class UDPPackageProtocol {

    /**
     * id位
     */
    private static final int ID_LENGTH = 32;
    /**
     * 分段数量位
     */
    private static final int RANGE_SIZE_LENGTH = 4;
    /**
     * 分段坐标位
     */
    private static final int RANGE_INDEX_LENGTH = 4;
    /**
     * 分段长度位
     */
    private static final int RANGE_LENGTH_LENGTH = 4;
    /**
     * 总长度位
     */
    private static final int CONTENT_LENGTH_LENGTH = 4;
    /**
     * 数据位
     */
    private static final int DATA_LENGTH = 1024 * 32;
    /**
     * 数据位总长度
     */
    private static final int PACK_LENGTH = ID_LENGTH + RANGE_SIZE_LENGTH + RANGE_INDEX_LENGTH + RANGE_LENGTH_LENGTH + CONTENT_LENGTH_LENGTH + DATA_LENGTH;

    private DatagramSocket receiveDatagramSocket;
    private DatagramPacket receiveDatagramPacket;
    private final ByteBuffer receiveBuffer = ByteBuffer.allocate(PACK_LENGTH);
    private final byte[] receiveBytes = new byte[PACK_LENGTH];
    private final byte[] receiveIdData = new byte[ID_LENGTH];
    private final byte[] receiveRangeSizeData = new byte[RANGE_SIZE_LENGTH];
    private final byte[] receiveRangeIndexData = new byte[RANGE_INDEX_LENGTH];
    private final byte[] receiveRangeLengthData = new byte[RANGE_LENGTH_LENGTH];
    private final byte[] receiveContentLengthData = new byte[CONTENT_LENGTH_LENGTH];
    private final byte[] receiveData = new byte[DATA_LENGTH];
    private final LinkedHashMap<String, LinkedList<ReceivePack>> mReceivePacks = new LinkedHashMap<>();

    private DatagramSocket sendDatagramSocket;
    private DatagramPacket sendDatagramPacket;
    private final ByteBuffer sendBuffer = ByteBuffer.allocate(PACK_LENGTH);
    private final byte[] sendBytes = new byte[PACK_LENGTH];

    /**
     * @param receiveDatagramSocket 接收socket
     */
    public UDPPackageProtocol(DatagramSocket receiveDatagramSocket) {
        this(receiveDatagramSocket, null, null, 0);
    }

    /**
     * @param sendDatagramSocket 发送socket
     * @param sendInetAddress    发送地址
     * @param sendPort           发送port
     */
    public UDPPackageProtocol(DatagramSocket sendDatagramSocket, InetAddress sendInetAddress, int sendPort) {
        this(null, sendDatagramSocket, sendInetAddress, sendPort);
    }

    /**
     * @param receiveDatagramSocket 接收socket
     * @param sendDatagramSocket    发送socket
     * @param sendInetAddress       发送地址
     * @param sendPort              发送port
     */
    public UDPPackageProtocol(DatagramSocket receiveDatagramSocket, DatagramSocket sendDatagramSocket, InetAddress sendInetAddress, int sendPort) {
        if (receiveDatagramSocket != null) {
            this.receiveDatagramSocket = receiveDatagramSocket;
            this.receiveDatagramPacket = new DatagramPacket(receiveBytes, receiveBytes.length);
        }
        if (sendDatagramSocket != null) {
            this.sendDatagramSocket = sendDatagramSocket;
            this.sendDatagramPacket = new DatagramPacket(new byte[0], 0, sendInetAddress, sendPort);
        }
    }


    /**
     * 开始接受数据，阻塞方法
     *
     * @throws InterruptedException 线程中断
     */
    public void receive(ReceiveCompleteCallback callback) throws InterruptedException {
        if (receiveDatagramSocket == null) {
            throw new RuntimeException(new NullPointerException("Receive DatagramSocket").getMessage());
        }
        while (!Thread.interrupted()) {
            try {
                /*响应数据拆分*/
                receiveDatagramSocket.receive(receiveDatagramPacket);
                receiveBuffer.position(0);
                receiveBuffer.put(receiveBytes);
                receiveBuffer.position(0);
                receiveBuffer.get(receiveIdData);
                receiveBuffer.get(receiveRangeSizeData);
                receiveBuffer.get(receiveRangeIndexData);
                receiveBuffer.get(receiveRangeLengthData);
                receiveBuffer.get(receiveContentLengthData);
                receiveBuffer.get(receiveData);

                /*数据片段包装*/
                ReceivePack pack = ReceivePack.obtain();
                pack.id = new String(receiveIdData);
                pack.rangeSize = Utils.bytes2int(receiveRangeSizeData);
                pack.rangeIndex = Utils.bytes2int(receiveRangeIndexData);
                pack.rangeLength = Utils.bytes2int(receiveRangeLengthData);
                pack.contentLength = Utils.bytes2int(receiveContentLengthData);
                pack.data = Utils.bytesCopy(receiveData);

                /*数据包装暂存*/
                LinkedList<ReceivePack> packList = mReceivePacks.get(pack.id) != null ? mReceivePacks.get(pack.id) : new LinkedList<>();
                packList.add(pack);
                mReceivePacks.put(pack.id, packList);

                /*数据完整性重组*/
                checkComplete(callback);

            } catch (IOException e) {
                if (receiveDatagramSocket.isClosed()) {
                    throw new InterruptedException(e.getMessage());
                } else {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 组合完整数据返回
     *
     * @param callback 数据回调接口
     */
    private void checkComplete(ReceiveCompleteCallback callback) {
        for (Map.Entry<String, LinkedList<ReceivePack>> entry : mReceivePacks.entrySet()) {
            LinkedList<ReceivePack> packs = entry.getValue();
            ReceivePack packOne = packs.getFirst();
            if (packs.size() == packOne.rangeSize) {
                String id = packOne.id;
                byte[] complete = new byte[packOne.contentLength];
                packs.sort(Comparator.comparingInt(o -> o.rangeIndex));
                int offset = 0;
                for (ReceivePack pack : packs) {
                    System.arraycopy(pack.data, 0, complete, offset, pack.rangeLength);
                    offset += pack.rangeLength;
                    pack.release();
                }
                callback.complete(id, complete);
                mReceivePacks.remove(id);
                return;
            }
        }
    }

    /**
     * 发送数据
     *
     * @param data 待发送数据
     * @throws IOException 异常
     */
    public void send(byte[] data) throws Exception {
        if (sendDatagramSocket == null) {
            throw new RuntimeException(new NullPointerException("Send DatagramSocket").getMessage());
        }
        int rangeSize = data.length % DATA_LENGTH != 0 ? data.length / DATA_LENGTH + 1 : data.length / DATA_LENGTH;
        byte[] idData = Utils.generateId(ID_LENGTH);
        byte[] rangeSizeData = Utils.int2bytes(rangeSize);
        byte[] contentLengthData = Utils.int2bytes(data.length);
        int length = 0;
        for (int i = 0; i < rangeSize; i++) {
            byte[] packData = new byte[DATA_LENGTH];
            if (length + DATA_LENGTH < data.length) {
                length += DATA_LENGTH;
            } else {
                length = data.length;
            }
            System.arraycopy(data, DATA_LENGTH * i, packData, 0, length - DATA_LENGTH * i);
            byte[] rangeIndexData = Utils.int2bytes(i + 1);
            byte[] rangeLengthData = Utils.int2bytes(length - DATA_LENGTH * i);
            sendBuffer.position(0);
            sendBuffer.put(idData);
            sendBuffer.put(rangeSizeData);
            sendBuffer.put(rangeIndexData);
            sendBuffer.put(rangeLengthData);
            sendBuffer.put(contentLengthData);
            sendBuffer.put(packData);
            sendBuffer.position(0);
            sendBuffer.get(sendBytes);
            sendDatagramPacket.setData(sendBytes);
            sendDatagramSocket.send(sendDatagramPacket);
        }
    }

    /**
     * 数据包片段
     */
    private static class ReceivePack {

        private static final LinkedList<ReceivePack> mCache = new LinkedList<>();

        String id;
        int rangeSize;
        int rangeIndex;
        int rangeLength;
        int contentLength;
        byte[] data;

        private static ReceivePack obtain() {
            if (mCache.size() > 0) {
                return mCache.pop();
            }
            return new ReceivePack();
        }

        void release() {
            id = null;
            rangeSize = 0;
            rangeIndex = 0;
            rangeLength = 0;
            contentLength = 0;
            data = null;
            mCache.add(this);
        }
    }

    /**
     * 完整数据回调接口
     */
    public interface ReceiveCompleteCallback {
        /**
         * 完整数据回调
         *
         * @param id   数据位id
         * @param data 数据
         */
        void complete(String id, byte[] data);
    }

}
