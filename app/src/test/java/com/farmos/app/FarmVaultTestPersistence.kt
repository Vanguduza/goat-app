package com.farmos.app

import java.io.File
import java.io.FileDescriptor
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Robolectric 4.17's ShadowLinux.open uses RandomAccessFile, which cannot open directories.
 * Use the host filesystem's real fsync for JVM tests; Android instrumentation uses the production API.
 */
internal object JvmVaultFileDurability : VaultFileDurability {
    override fun syncFile(descriptor: FileDescriptor) = descriptor.sync()
    override fun syncDirectory(directory: File) {
        FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
    }
}

internal fun testFarmKeyVault(
    directory: File,
    sealer: DeviceSealer,
    durability: VaultFileDurability = JvmVaultFileDurability,
) = FarmKeyVault(directory, sealer, durability)
