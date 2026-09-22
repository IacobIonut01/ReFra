/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.smb

import com.dot.gallery.cloud.core.CloudServerConfig
import com.dot.gallery.cloud.core.CloudTrace
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.netfs.FileSystemBackend
import com.dot.gallery.cloud.netfs.NetFsConnection
import com.dot.gallery.cloud.netfs.NetFsEntry
import com.dot.gallery.cloud.netfs.NetFsStorage
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.io.InputStream
import java.util.EnumSet

/**
 * SMB2/3 backend powered by smbj. Connection parameters are encoded in the config's
 * `serverUrl` as `smb://host[:port]/share/optional/base/path`; credentials come from
 * `username` (`DOMAIN\user`, `user@domain`, or plain `user`) and `password`.
 */
class SmbBackend : FileSystemBackend {

    override val providerType: ProviderType = ProviderType.SMB
    override val displayName: String = "SMB"

    private class SmbConnection(
        val client: SMBClient,
        val connection: Connection,
        val session: Session,
        val share: DiskShare,
        val host: String,
        val shareName: String,
        val basePath: String
    ) : NetFsConnection() {
        override val rootDisplay: String =
            listOf(host, shareName, basePath).filter { it.isNotEmpty() }.joinToString("/")

        override fun isAlive(): Boolean = connection.isConnected
    }

    override fun connect(config: CloudServerConfig): NetFsConnection {
        val raw = config.serverUrl.removePrefix("smb://").removePrefix("SMB://").trimEnd('/')
        val firstSlash = raw.indexOf('/')
        val hostPart = if (firstSlash < 0) raw else raw.substring(0, firstSlash)
        val rest = if (firstSlash < 0) "" else raw.substring(firstSlash + 1)
        val host = hostPart.substringBefore(':')
        val port = hostPart.substringAfter(':', "").toIntOrNull()
        val shareName = rest.substringBefore('/')
        val basePath = rest.substringAfter('/', "")
        require(host.isNotEmpty() && shareName.isNotEmpty()) { "Invalid SMB URL: ${config.serverUrl}" }

        val rawUser = config.username.orEmpty()
        val (domain, user) = when {
            rawUser.contains('\\') -> rawUser.substringBefore('\\') to rawUser.substringAfter('\\')
            rawUser.contains('@') -> rawUser.substringAfter('@') to rawUser.substringBefore('@')
            else -> "" to rawUser
        }
        val password = config.password.orEmpty()

        val client = SMBClient()
        val connection = if (port != null) client.connect(host, port) else client.connect(host)
        val auth = if (user.isEmpty()) {
            AuthenticationContext.anonymous()
        } else {
            AuthenticationContext(user, password.toCharArray(), domain)
        }
        val session = connection.authenticate(auth)
        val share = session.connectShare(shareName) as DiskShare
        // Throughput diagnostic: smbj caps each SMB2 READ at the negotiated max read size, so a small
        // value (e.g. 64 KB) over a high-latency link bottlenecks transfers regardless of our buffer.
        runCatching {
            val np = connection.connectionContext.negotiatedProtocol
            CloudTrace.d(
                "SMB connected $host/$shareName — dialect=${np.dialect} " +
                    "maxRead=${CloudTrace.bytes(np.maxReadSize.toLong())} " +
                    "maxWrite=${CloudTrace.bytes(np.maxWriteSize.toLong())}"
            )
        }
        return SmbConnection(client, connection, session, share, host, shareName, basePath)
    }

    override fun listDir(conn: NetFsConnection, path: String): List<NetFsEntry> {
        val c = conn as SmbConnection
        val dir = fullPath(c, path)
        return c.share.list(dir)
            .filter { it.fileName != "." && it.fileName != ".." }
            .map { info ->
                val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                val rel = if (path.isEmpty()) info.fileName else "$path/${info.fileName}"
                NetFsEntry(
                    name = info.fileName,
                    relativePath = rel,
                    isDirectory = isDir,
                    size = info.endOfFile,
                    lastModified = runCatching { info.lastWriteTime.toDate().time }.getOrDefault(0L)
                )
            }
    }

    override fun openRead(conn: NetFsConnection, path: String, offset: Long): InputStream {
        val c = conn as SmbConnection
        val file = c.share.openFile(
            fullPath(c, path),
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        return SmbOffsetInputStream(file, offset)
    }

    override fun fileSize(conn: NetFsConnection, path: String): Long {
        val c = conn as SmbConnection
        return c.share.getFileInformation(fullPath(c, path)).standardInformation.endOfFile
    }

    override fun exists(conn: NetFsConnection, path: String): Boolean {
        val c = conn as SmbConnection
        return c.share.fileExists(fullPath(c, path))
    }

    override fun write(conn: NetFsConnection, path: String, data: InputStream, size: Long) {
        val c = conn as SmbConnection
        val segments = path.trim('/').split('/').filter(String::isNotEmpty)
        var parent = ""
        segments.dropLast(1).forEach { segment ->
            parent = if (parent.isEmpty()) segment else "$parent/$segment"
            val fullParent = fullPath(c, parent)
            if (!c.share.folderExists(fullParent)) c.share.mkdir(fullParent)
        }
        val file = c.share.openFile(
            fullPath(c, path),
            EnumSet.of(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        )
        try {
            file.outputStream.use { data.copyTo(it) }
        } catch (e: Exception) {
            runCatching { file.close() }
            throw e
        }
    }

    override fun delete(conn: NetFsConnection, path: String) {
        val c = conn as SmbConnection
        c.share.rm(fullPath(c, path))
    }

    override fun mkdir(conn: NetFsConnection, path: String) {
        val c = conn as SmbConnection
        if (!c.share.folderExists(fullPath(c, path))) c.share.mkdir(fullPath(c, path))
    }

    override fun storage(conn: NetFsConnection): NetFsStorage? = try {
        val c = conn as SmbConnection
        val info = c.share.shareInformation
        NetFsStorage(
            usedBytes = (info.totalSpace - info.freeSpace).coerceAtLeast(0L),
            totalBytes = info.totalSpace,
            freeBytes = info.freeSpace
        )
    } catch (_: Exception) {
        null
    }

    override fun close(conn: NetFsConnection) {
        val c = conn as SmbConnection
        runCatching { c.share.close() }
        runCatching { c.session.close() }
        runCatching { c.connection.close() }
        runCatching { c.client.close() }
    }

    /** Joins the configured base path with a forward-slash relative path into an SMB (backslash) path. */
    private fun fullPath(c: SmbConnection, rel: String): String =
        listOf(c.basePath, rel).filter { it.isNotEmpty() }.joinToString("/").replace('/', '\\')

    /** InputStream backed by smbj random-access reads, enabling HTTP Range/seek support. */
    private class SmbOffsetInputStream(private val file: File, private var position: Long) : InputStream() {
        private val single = ByteArray(1)

        override fun read(): Int {
            val n = read(single, 0, 1)
            return if (n <= 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = file.read(b, position, off, len)
            if (n > 0) position += n
            return n
        }

        override fun close() {
            runCatching { file.close() }
        }
    }
}
