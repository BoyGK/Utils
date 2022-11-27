package com.nullpt.utils

import com.nullpt.utils.sockets.*
import java.util.concurrent.Executors

class Main {

}

fun main() {

    testNio()
    //test1()
}

fun testNio() {
    val selectorServer = SelectorServer(6789, Executors.newCachedThreadPool())
    selectorServer.accept()
    selectorServer.setSelectorIO(object : SelectorIO {
        override fun accept(code: Int) {

        }

        override fun receive(code: Int, byteArray: ByteArray) {
            println("server :" + String(byteArray))
            selectorServer.send(code, "receive".toByteArray())
        }

    })

    val selectorClient = SelectorClient("127.0.0.1", 6789, Executors.newCachedThreadPool())
    selectorClient.connect()
    selectorClient.setSelectorIO(object : SelectorIO {
        override fun receive(code: Int, byteArray: ByteArray) {
            println("client :" + String(byteArray))
        }
    })

    selectorClient.send("123456".toByteArray())

    Thread.sleep(1000)

    selectorClient.close()

    selectorServer.stop()
}

/**
 * 测试socket链接、关闭
 */
fun test1() {
    val socketServer = SocketServer(6789)
    socketServer.accept { code ->
        socketServer.receive(code) { data ->
            println(String(data))
            socketServer.send(code, "server receive -> ${String(data)}".toByteArray())
        }
    }

    val socketClient1 = SocketClient("127.0.0.1", 6789)
    socketClient1.connect()
    socketClient1.receive {
        println(String(it))
    }
    socketClient1.send("client1 send 123456789".toByteArray())

    val socketClient2 = SocketClient("127.0.0.1", 6789)
    socketClient2.connect()
    socketClient2.receive {
        println(String(it))
    }
    socketClient2.send("client2 send kajbsdkkajbksjdk".toByteArray())

    Thread.sleep(1000)

    socketClient1.close()

    socketClient2.send("re send a45s6d".toByteArray())

    Thread.sleep(1000)
    socketClient2.close()

    val socketClient3 = SocketClient("127.0.0.1", 6789)
    socketClient3.connect()
    socketClient3.receive {
        println(String(it))
    }
    socketClient3.send("client3 send 55555".toByteArray())

    //socketServer.stop()

    Thread.sleep(1000)

    socketClient3.send("client3 send 55555".toByteArray())
    socketClient3.close()

    socketServer.stop()
}