package com.nullpt.utils.sockets

import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 长连接
 */
class SocketClient(
    private val ip: String,
    private val port: Int,
    private val executorService: ExecutorService = Executors.newCachedThreadPool()
) {

    private var mSocket: Socket? = null
    private val mTCPPackageProtocol = TCPPackageProtocol()

    fun connect() {
        if (mSocket != null) {
            return
        }
        mSocket = Socket(ip, port)
    }

    fun send(data: ByteArray) {
        try {
            val socket = mSocket ?: return
            mTCPPackageProtocol.send(socket.getOutputStream(), data)
        } catch (e: Exception) {
            /* no-op */
        }
    }

    fun receive(receiver: (ByteArray) -> Unit) {
        executorService.execute {
            try {
                val socket = mSocket ?: return@execute
                mTCPPackageProtocol.receive(socket.getInputStream()) { data, length ->
                    receiver.invoke(data.copyOfRange(0, length))
                }
            } catch (e: Exception) {
                Thread.currentThread().interrupt()
            }
        }
    }

    fun close() {
        val socket = mSocket ?: return
        try {
            socket.shutdownInput()
        } catch (e: Exception) {
            /* no-op */
        }
        try {
            socket.shutdownOutput()
        } catch (e: Exception) {
            /* no-op */
        }
        try {
            socket.close()
        } catch (e: Exception) {
            /* no-op */
        }
    }

}