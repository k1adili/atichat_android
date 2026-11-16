package com.k1adili.atichat.core

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** Result of queueing a file for sending. */
data class FileTransfer(val messageId: String, val timestamp: String)

/**
 * LAN discovery, UDP public chat and TLS-protected direct transfers.
 * Wire-compatible with the Windows app: UDP 37020 presence/public/typing, TLS 37021 length-prefixed JSON frames.
 * Pure JVM (no Android classes) so it can be exercised against the Python implementation.
 */
class LanNode(
    val userId: String,
    username: String,
    avatar: String,
    certDer: ByteArray,
    keyPkcs8: ByteArray,
    private val downloadsDir: File,
    private val udpPort: Int = Protocol.UDP_PORT,
    private val tcpPort: Int = Protocol.TCP_PORT,
    /** Where presence announcements go: limited broadcast plus every directed (subnet) broadcast. */
    private val broadcastTargets: () -> List<InetSocketAddress> = { defaultBroadcastTargets(udpPort) },
    /**
     * Where public messages go. Defaults to directed broadcasts only: sending the same message to both
     * 255.255.255.255 and the subnet address would make Windows peers show it twice.
     */
    private val publicTargets: () -> List<InetSocketAddress> = { directedBroadcastTargets(udpPort) },
    private val listener: (NetEvent) -> Unit,
) {
    @Volatile var username: String = username
        private set
    @Volatile var avatar: String = avatar
        private set

    /** SHA-256 (hex) of the DER certificate; announced so peers can pin it. */
    val fingerprint: String = Protocol.sha256Hex(certDer)

    private val serverContext: SSLContext
    private val clientContext: SSLContext

    private val stopped = java.util.concurrent.atomic.AtomicBoolean(false)
    private val peers = HashMap<String, Peer>()
    private val peersLock = Any()
    private val senders = ConcurrentHashMap<String, ExecutorService>()
    private val handlerSlots = Semaphore(MAX_CONCURRENT_RECEIVES)
    private val handlerPool: ExecutorService = Executors.newCachedThreadPool(daemonFactory("AtiChat-receive"))

    @Volatile private var udpSocket: DatagramSocket? = null
    @Volatile private var tcpSocket: SSLServerSocket? = null
    private val threads = mutableListOf<Thread>()

    init {
        downloadsDir.mkdirs()
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyPkcs8))
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("peer", privateKey, KEY_PASSWORD, arrayOf(certificate))
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore, KEY_PASSWORD) }
            .keyManagers
        serverContext = SSLContext.getInstance("TLS").apply { init(keyManagers, null, null) }
        // Peers use self-signed certificates, so chain validation is meaningless here; authenticity comes from
        // pinning the SHA-256 fingerprint announced over UDP (checked in sendTcp after the handshake).
        clientContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(AcceptAnyChain), null) }
    }

    // ----------------------------------------------------------------------------------------------- lifecycle

    fun start() {
        check(udpSocket == null) { "already started" }
        val any = InetAddress.getByName("0.0.0.0")
        val udp = DatagramSocket(null)
        val tcp = serverContext.serverSocketFactory.createServerSocket() as SSLServerSocket
        try {
            udp.reuseAddress = true
            udp.broadcast = true
            udp.bind(InetSocketAddress(any, udpPort))
            udp.soTimeout = 500
            tcp.reuseAddress = true
            tcp.enabledProtocols = tlsProtocols(tcp.supportedProtocols)
            tcp.bind(InetSocketAddress(any, tcpPort), 16)
            tcp.soTimeout = 500
        } catch (error: IOException) {
            udp.close()
            tcp.close()
            throw error
        }
        udpSocket = udp
        tcpSocket = tcp
        threads += Thread({ udpLoop(udp) }, "AtiChat-UDP").apply { isDaemon = true; start() }
        threads += Thread({ tcpLoop(tcp) }, "AtiChat-TCP").apply { isDaemon = true; start() }
        announcePresence()
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        senders.values.forEach { it.shutdownNow() }
        senders.clear()
        handlerPool.shutdownNow()
        udpSocket?.close()
        tcpSocket?.close()
        udpSocket = null
        tcpSocket = null
        threads.forEach { it.join(1000) }
        threads.clear()
    }

    fun setProfile(username: String, avatar: String) {
        this.username = username
        this.avatar = avatar
        announce()
    }

    /** Announce this user immediately and ask everyone else to announce themselves. */
    fun announcePresence() {
        announce()
        broadcast(linkedMapOf("type" to "presence_request", "user_id" to userId), reportFailure = false)
    }

    fun peers(): List<Peer> = synchronized(peersLock) { peers.values.toList() }

    // ----------------------------------------------------------------------------------------------- sending

    /** Broadcasts a public message. Throws [IOException] if it could not be sent on any interface. */
    fun sendPublic(body: String, messageId: String, timestamp: String) {
        broadcast(
            linkedMapOf(
                "type" to "public_message",
                "user_id" to userId,
                "username" to username,
                "message_id" to messageId,
                "timestamp" to timestamp,
                "body" to body.take(Protocol.MAX_MESSAGE_LENGTH),
            ),
            reportFailure = true,
            targets = publicTargets,
        )
    }

    fun sendTyping(peerId: String) {
        val peer = synchronized(peersLock) { peers[peerId] } ?: return
        sendUdp(
            linkedMapOf("type" to "typing", "user_id" to userId, "username" to username),
            InetSocketAddress(peer.ip, udpPort),
        )
    }

    /** Queues a private message. Throws [IllegalStateException] when the peer is offline. */
    fun sendDirect(peerId: String, body: String, messageId: String, timestamp: String) {
        val peer = requirePeer(peerId)
        val packet = linkedMapOf<String, Any?>(
            "type" to "direct_message",
            "user_id" to userId,
            "username" to username,
            "recipient_id" to peerId,
            "message_id" to messageId,
            "timestamp" to timestamp,
            "body" to body.take(Protocol.MAX_MESSAGE_LENGTH),
        )
        enqueue(peer, packet, null)
    }

    /** Queues a file. [open] is called on the sender thread and must return exactly [size] bytes. */
    fun sendFile(peerId: String, fileName: String, size: Long, open: () -> InputStream): FileTransfer {
        require(size in 0..Protocol.MAX_FILE_SIZE) {
            "حداکثر اندازهٔ فایل باید ${Protocol.MAX_FILE_SIZE / (1024 * 1024)} مگابایت باشد."
        }
        val peer = requirePeer(peerId)
        val messageId = UUID.randomUUID().toString()
        val timestamp = Protocol.nowTimestamp()
        val packet = linkedMapOf<String, Any?>(
            "type" to "file",
            "user_id" to userId,
            "username" to username,
            "recipient_id" to peerId,
            "message_id" to messageId,
            "timestamp" to timestamp,
            "filename" to fileName,
            "size" to size,
        )
        enqueue(peer, packet, open)
        return FileTransfer(messageId, timestamp)
    }

    private fun requirePeer(peerId: String): Peer =
        synchronized(peersLock) { peers[peerId] } ?: throw IllegalStateException("این کاربر دیگر برخط نیست.")

    private fun enqueue(peer: Peer, packet: Map<String, Any?>, open: (() -> InputStream)?) {
        if (stopped.get()) throw IllegalStateException("شبکه متوقف شده است.")
        // One single-threaded executor per peer keeps messages and files in the order they were queued.
        val executor = senders.computeIfAbsent(peer.userId) {
            Executors.newSingleThreadExecutor(daemonFactory("AtiChat-send-${peer.userId.take(8)}"))
        }
        executor.execute { sendTcp(peer, packet, open) }
    }

    private fun sendTcp(peer: Peer, packet: Map<String, Any?>, open: (() -> InputStream)?) {
        try {
            Socket().use { raw ->
                raw.connect(InetSocketAddress(peer.ip, peer.tcpPort), CONNECT_TIMEOUT_MS)
                raw.soTimeout = IO_TIMEOUT_MS
                val tls = clientContext.socketFactory.createSocket(raw, peer.ip, peer.tcpPort, true) as SSLSocket
                tls.use {
                    tls.enabledProtocols = tlsProtocols(tls.supportedProtocols)
                    tls.startHandshake()
                    val remote = tls.session.peerCertificates.firstOrNull()?.encoded
                        ?: throw SSLException("certificate missing")
                    if (Protocol.sha256Hex(remote) != peer.fingerprint) {
                        throw SSLException("گواهی امنیتی همتا با اطلاعات شناسایی شبکه مطابقت ندارد.")
                    }
                    val out = tls.outputStream
                    writeFrame(out, packet)
                    if (open != null) {
                        val size = (packet["size"] as Number).toLong()
                        open().use { source -> copyExactly(source, out, size) }
                    }
                    out.flush()
                    val ack = tls.inputStream.read()
                    if (ack != Protocol.TRANSFER_ACK) throw IOException("همتا دریافت پیام را تأیید نکرد.")
                }
            }
            if (packet["type"] == "file") {
                emit(
                    NetEvent.FileSent(
                        peerId = peer.userId,
                        peerName = peer.username,
                        messageId = packet["message_id"] as String,
                        timestamp = packet["timestamp"] as String,
                        fileName = packet["filename"] as String,
                    ),
                )
            }
        } catch (error: Exception) {
            if (stopped.get()) return
            emit(
                NetEvent.Failure(
                    message = "ارسال به ${peer.username} ناموفق بود.",
                    messageId = packet["message_id"] as? String,
                    peerId = peer.userId,
                    transferType = packet["type"] as? String,
                ),
            )
        }
    }

    private fun writeFrame(out: java.io.OutputStream, packet: Map<String, Any?>) {
        val payload = Json.encode(packet).toByteArray(Charsets.UTF_8)
        if (payload.size > Protocol.MAX_TCP_HEADER) throw IOException("اطلاعات همراه پیام بیش از حد مجاز است.")
        out.write(ByteBuffer.allocate(4).putInt(payload.size).array())
        out.write(payload)
    }

    private fun copyExactly(source: InputStream, out: java.io.OutputStream, size: Long) {
        val buffer = ByteArray(Protocol.FILE_CHUNK_SIZE)
        var remaining = size
        while (remaining > 0) {
            val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IOException("فایل در میانهٔ ارسال تغییر کرد.")
            out.write(buffer, 0, read)
            remaining -= read
        }
    }

    // ----------------------------------------------------------------------------------------------- receiving (TCP)

    private fun tcpLoop(server: SSLServerSocket) {
        while (!stopped.get()) {
            val client = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: IOException) {
                if (!stopped.get()) emit(NetEvent.Failure("شنود ارتباط مستقیم متوقف شد."))
                return
            }
            if (!handlerSlots.tryAcquire()) {
                runCatching { client.close() }
                continue
            }
            try {
                handlerPool.execute {
                    try {
                        handleTcp(client as SSLSocket)
                    } finally {
                        handlerSlots.release()
                    }
                }
            } catch (_: Exception) {
                handlerSlots.release()
                runCatching { client.close() }
            }
        }
    }

    private fun handleTcp(socket: SSLSocket) {
        val ip = (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress ?: "?"
        try {
            socket.use {
                socket.soTimeout = IO_TIMEOUT_MS
                socket.startHandshake()
                val input = DataInputStream(socket.inputStream)
                val headerLength = input.readInt()
                if (headerLength !in 1..Protocol.MAX_TCP_HEADER) throw IOException("bad header length")
                val header = ByteArray(headerLength).also { input.readFully(it) }
                val packet = Json.parseObject(String(header, Charsets.UTF_8))
                if (packet["recipient_id"] != userId) throw IOException("wrong recipient")
                val senderId = packet["user_id"] as? String ?: throw IOException("bad sender")
                val senderName = packet["username"] as? String ?: throw IOException("bad sender")
                val messageId = packet["message_id"] as? String ?: throw IOException("bad message id")
                val timestamp = packet["timestamp"] as? String ?: throw IOException("bad timestamp")
                when (packet["type"]) {
                    "direct_message" -> {
                        val body = packet["body"] as? String ?: throw IOException("bad body")
                        if (body.codePointCount(0, body.length) > Protocol.MAX_MESSAGE_LENGTH) {
                            throw IOException("body too long")
                        }
                        emit(
                            NetEvent.DirectMessage(
                                senderId,
                                Protocol.limitCodePoints(senderName, Protocol.MAX_NAME_LENGTH),
                                messageId,
                                timestamp,
                                body,
                                ip,
                            ),
                        )
                    }
                    "file" -> receiveFile(input, packet, senderId, senderName, messageId, timestamp, ip)
                    else -> throw IOException("unsupported type")
                }
                socket.outputStream.write(Protocol.TRANSFER_ACK)
                socket.outputStream.flush()
            }
        } catch (error: Exception) {
            if (!stopped.get()) emit(NetEvent.Failure("دریافت پیام یا فایل از $ip ناموفق بود."))
        }
    }

    private fun receiveFile(
        input: DataInputStream,
        packet: Map<String, Any?>,
        senderId: String,
        senderName: String,
        messageId: String,
        timestamp: String,
        ip: String,
    ) {
        val size = (packet["size"] as? Long) ?: throw IOException("bad size")
        val filename = packet["filename"] as? String
        if (size !in 0..Protocol.MAX_FILE_SIZE) throw IOException("bad size")
        if (filename.isNullOrEmpty() || filename.length > 255) throw IOException("bad file name")
        val safeName = Protocol.sanitizeFileName(filename)
        downloadsDir.mkdirs()
        if (downloadsDir.usableSpace < size + MIN_FREE_SPACE_BYTES) throw IOException("not enough space")
        val storedName = "${UUID.randomUUID().toString().replace("-", "").take(8)}_$safeName"
        val destination = File(downloadsDir, storedName)
        val temporary = File(downloadsDir, "$storedName.part")
        try {
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(Protocol.FILE_CHUNK_SIZE)
                var remaining = size
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) throw IOException("connection closed")
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
            if (!temporary.renameTo(destination)) throw IOException("rename failed")
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
        emit(
            NetEvent.FileReceived(
                userId = senderId,
                username = Protocol.limitCodePoints(senderName, Protocol.MAX_NAME_LENGTH),
                messageId = messageId,
                timestamp = timestamp,
                displayName = safeName,
                storedName = storedName,
                file = destination,
                size = size,
                ip = ip,
            ),
        )
    }

    // ----------------------------------------------------------------------------------------------- UDP

    private fun udpLoop(udp: DatagramSocket) {
        var nextHeartbeat = 0L
        val buffer = ByteArray(Protocol.MAX_UDP_PACKET)
        while (!stopped.get()) {
            val now = System.nanoTime()
            if (now >= nextHeartbeat) {
                announce()
                nextHeartbeat = now + TimeUnit.SECONDS.toNanos(Protocol.HEARTBEAT_SECONDS)
            }
            expirePeers(now)
            val datagram = DatagramPacket(buffer, buffer.size)
            try {
                udp.receive(datagram)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: IOException) {
                if (stopped.get() || udp.isClosed) return
                continue
            }
            try {
                val text = String(datagram.data, datagram.offset, datagram.length, Charsets.UTF_8)
                handleUdp(Json.parseObject(text), datagram.address.hostAddress ?: continue)
            } catch (_: IllegalArgumentException) {
                // Not one of ours (or malformed): ignore like the Windows app.
            }
        }
    }

    private fun handleUdp(packet: Map<String, Any?>, ip: String) {
        val kind = packet["type"]
        val peerId = packet["user_id"] as? String ?: return
        if (peerId == userId) return
        when (kind) {
            "presence_request" -> sendUdp(presencePacket(), InetSocketAddress(ip, udpPort))
            "presence" -> {
                val peer = Protocol.parsePresence(packet, ip, System.nanoTime()) ?: return
                val previous = synchronized(peersLock) {
                    val old = peers[peerId]
                    peers[peerId] = peer
                    old
                }
                if (previous == null) {
                    emit(NetEvent.PeerOnline(peer))
                } else if (
                    previous.username != peer.username || previous.avatar != peer.avatar ||
                    previous.ip != peer.ip || previous.tcpPort != peer.tcpPort ||
                    previous.fingerprint != peer.fingerprint
                ) {
                    emit(NetEvent.PeerUpdated(peer))
                }
            }
            "public_message" -> {
                val body = packet["body"] as? String ?: return
                val username = packet["username"] as? String ?: return
                val messageId = packet["message_id"] as? String ?: return
                val timestamp = packet["timestamp"] as? String ?: return
                if (body.codePointCount(0, body.length) > Protocol.MAX_MESSAGE_LENGTH) return
                emit(
                    NetEvent.PublicMessage(
                        peerId,
                        Protocol.limitCodePoints(username, Protocol.MAX_NAME_LENGTH),
                        messageId,
                        timestamp,
                        body,
                    ),
                )
            }
            "typing" -> {
                val peer = synchronized(peersLock) { peers[peerId] } ?: return
                emit(NetEvent.Typing(peerId, peer.username))
            }
        }
    }

    private fun expirePeers(now: Long) {
        val expired = mutableListOf<Peer>()
        synchronized(peersLock) {
            val iterator = peers.values.iterator()
            while (iterator.hasNext()) {
                val peer = iterator.next()
                if (now - peer.lastSeenNanos > TimeUnit.SECONDS.toNanos(Protocol.PEER_TIMEOUT_SECONDS)) {
                    expired += peer
                    iterator.remove()
                }
            }
        }
        expired.forEach { emit(NetEvent.PeerOffline(it)) }
    }

    private fun presencePacket() =
        Protocol.presencePacket(userId, username, avatar, tcpPort, fingerprint)

    private fun announce() = broadcast(presencePacket(), reportFailure = false)

    private fun broadcast(
        packet: Map<String, Any?>,
        reportFailure: Boolean,
        targets: () -> List<InetSocketAddress> = broadcastTargets,
    ) {
        var delivered = false
        var lastError: IOException? = null
        for (target in targets()) {
            try {
                if (sendUdpOrThrow(packet, target)) delivered = true
            } catch (error: IOException) {
                lastError = error
            }
        }
        if (reportFailure && !delivered) {
            throw lastError ?: IOException("خطا در ارتباط با شبکهٔ محلی.")
        }
    }

    private fun sendUdp(packet: Map<String, Any?>, target: InetSocketAddress) {
        try {
            sendUdpOrThrow(packet, target)
        } catch (_: IOException) {
            // Best effort (typing indicators, presence replies).
        }
    }

    private fun sendUdpOrThrow(packet: Map<String, Any?>, target: InetSocketAddress): Boolean {
        val udp = udpSocket ?: return false
        val payload = Json.encode(packet).toByteArray(Charsets.UTF_8)
        if (payload.size > Protocol.MAX_UDP_PACKET) throw IOException("اندازهٔ بستهٔ شبکه از حد مجاز بیشتر است.")
        udp.send(DatagramPacket(payload, payload.size, target))
        return true
    }

    private fun emit(event: NetEvent) {
        try {
            listener(event)
        } catch (_: Exception) {
            // A misbehaving listener must never kill a network thread.
        }
    }

    private object AcceptAnyChain : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val IO_TIMEOUT_MS = 30_000
        private const val MAX_CONCURRENT_RECEIVES = 8
        private const val MIN_FREE_SPACE_BYTES = 16L * 1024 * 1024
        private val KEY_PASSWORD = "atichat".toCharArray()

        private fun tlsProtocols(supported: Array<String>): Array<String> =
            supported.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()

        private fun daemonFactory(name: String) = java.util.concurrent.ThreadFactory { runnable ->
            Thread(runnable, name).apply { isDaemon = true }
        }

        /** 255.255.255.255 plus the directed broadcast address of every active IPv4 interface (Wi-Fi, hotspot...). */
        fun defaultBroadcastTargets(port: Int): List<InetSocketAddress> {
            val addresses = LinkedHashSet<InetAddress>()
            addresses += InetAddress.getByName("255.255.255.255")
            collectDirectedBroadcasts(addresses)
            return addresses.map { InetSocketAddress(it, port) }
        }

        /** Directed broadcasts of active IPv4 interfaces; falls back to 255.255.255.255 when there are none. */
        fun directedBroadcastTargets(port: Int): List<InetSocketAddress> {
            val addresses = LinkedHashSet<InetAddress>()
            collectDirectedBroadcasts(addresses)
            if (addresses.isEmpty()) addresses += InetAddress.getByName("255.255.255.255")
            return addresses.map { InetSocketAddress(it, port) }
        }

        private fun collectDirectedBroadcasts(addresses: MutableSet<InetAddress>) {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces != null && interfaces.hasMoreElements()) {
                    val nif = interfaces.nextElement()
                    if (!nif.isUp || nif.isLoopback) continue
                    for (address in nif.interfaceAddresses) {
                        val broadcast = address.broadcast
                        if (broadcast is Inet4Address) addresses += broadcast
                    }
                }
            } catch (_: SocketException) {
                // Fall back to the limited broadcast address only.
            }
        }
    }
}
