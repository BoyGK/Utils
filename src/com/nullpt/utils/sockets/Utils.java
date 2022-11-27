package com.nullpt.utils.sockets;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * byte[]数组拼接，拆包工具类
 */
public class Utils {

    /**
     * int转byte[]
     *
     * @param num 带转换数字
     * @return 转换后的4位byte[]
     */
    public static byte[] int2bytes(int num) {
        byte[] bytes = new byte[4];
        for (int ix = 0; ix < 4; ++ix) {
            int offset = 32 - (ix + 1) * 8;
            bytes[ix] = (byte) ((num >> offset) & 0xff);
        }
        return bytes;
    }

    /**
     * byte[]转int
     *
     * @param bytes 4位byte[]
     * @return 转换后数字
     */
    public static int bytes2int(byte[] bytes) {
        int num = 0;
        for (int ix = 0; ix < 4; ++ix) {
            num <<= 8;
            num |= (bytes[ix] & 0xff);
        }
        return num;
    }

    /**
     * 拷贝byte[]
     *
     * @param bytes 源byte[]
     * @return 新的byte[]
     */
    public static byte[] bytesCopy(byte[] bytes) {
        byte[] newBytes = new byte[bytes.length];
        System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
        return newBytes;
    }

    /**
     * @param num 位数
     * @return 返回{@code num}位id
     */
    public static byte[] generateId(int num) {
        StringBuilder temp = new StringBuilder("" + System.currentTimeMillis());
        while (temp.length() < num) {
            temp.insert(0, "0");
        }
        while (temp.length() > num) {
            temp.deleteCharAt(0);
        }
        byte[] result = temp.toString().getBytes(StandardCharsets.UTF_8);
        assert result.length == num;
        return result;
    }

    /**
     * 获取本机的真实ip
     *
     * @return ip
     */
    private String getRealIP() {
        try {
            //返回本机的所有接口，枚举类型;
            Enumeration<NetworkInterface> networkInterfaceEnumeration = NetworkInterface.getNetworkInterfaces();
            //枚举进行遍历
            while (networkInterfaceEnumeration.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaceEnumeration.nextElement();
                Enumeration<InetAddress> inetAddressEnumeration = networkInterface.getInetAddresses();
                //枚举进行遍历
                while (inetAddressEnumeration.hasMoreElements()) {
                    InetAddress inetAddress = inetAddressEnumeration.nextElement();
                    //当不是回路地址且是IPV4时
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

}
