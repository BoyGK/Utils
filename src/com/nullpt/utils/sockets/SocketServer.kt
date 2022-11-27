package com.nullpt.utils.sockets

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 长连接服务
 */
class SocketServer(
    private val port: Int,
    private val executorService: ExecutorService = Executors.newCachedThreadPool()
) {

    private val mExecutorService = Executors.newCachedThreadPool()
    private var mServerSocket: ServerSocket? = null
    private val mSocketMap = mutableMapOf<Int, Socket>()
    private val mReceiverMap = mutableMapOf<Int, ReceiverRunnable>()
    private val mTCPPackageProtocol = TCPPackageProtocol()

    fun accept(accept: (Int) -> Unit) {
        if (mServerSocket != null) {
            return
        }
        mServerSocket = ServerSocket(port)
        startAccept(accept)
    }

    private fun startAccept(accept: (Int) -> Unit) {
        executorService.execute {
            while (!Thread.interrupted()) {
                try {
                    val socket = mServerSocket?.accept() ?: continue
                    val receiver = ReceiverRunnable(socket)
                    mSocketMap[socket.hashCode()] = socket
                    mReceiverMap[socket.hashCode()] = receiver
                    mExecutorService.execute(receiver)
                    accept.invoke(socket.hashCode())
                } catch (e: Exception) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    fun send(code: Int, data: ByteArray) {
        try {
            val socket = mSocketMap[code] ?: return
            mTCPPackageProtocol.send(socket.getOutputStream(), data)
        } catch (e: Exception) {
            /* no-op */
        }
    }

    fun receive(code: Int, receiver: (ByteArray) -> Unit) {
        mReceiverMap[code]?.setReceiver(receiver)
    }

    fun close(code: Int) {
        val socket = mSocketMap[code] ?: return
        mSocketMap.remove(socket.hashCode())
        mReceiverMap.remove(socket.hashCode())
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

    fun stop() {
        mSocketMap.forEach {
            try {
                it.value.shutdownInput()
            } catch (e: Exception) {
                /* no-op */
            }
            try {
                it.value.shutdownOutput()
            } catch (e: Exception) {
                /* no-op */
            }
            try {
                it.value.close()
            } catch (e: Exception) {
                /* no-op */
            }
        }
        mSocketMap.clear()
        mServerSocket?.close()
    }

    private class ReceiverRunnable(private val socket: Socket) : Runnable {
        private var mReceiver: ((ByteArray) -> Unit)? = null
        private val mTCPPackageProtocol = TCPPackageProtocol()

        fun setReceiver(receiver: (ByteArray) -> Unit) {
            mReceiver = receiver
        }

        override fun run() {
            try {
                mTCPPackageProtocol.receive(socket.getInputStream()) { data, length ->
                    mReceiver?.invoke(data.copyOfRange(0, length))
                }
            } catch (e: Exception) {
                Thread.currentThread().interrupt()
            }
        }
    }

}