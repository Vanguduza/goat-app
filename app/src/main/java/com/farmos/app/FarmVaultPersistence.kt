package com.farmos.app

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Small I/O seam for injected storage failures; successful saves require both durability barriers. */
internal interface VaultFileDurability {
    fun syncFile(descriptor: FileDescriptor)
    fun syncDirectory(directory: File)
}

/** Android's public POSIX API; the application supports API 26 and these calls exist from API 21. */
internal object AndroidVaultFileDurability : VaultFileDurability {
    override fun syncFile(descriptor: FileDescriptor) = descriptor.sync()

    override fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try {
            // O_DIRECTORY is not in Android's public SDK. Check the opened descriptor itself,
            // rather than a path that could change between a separate directory check and open.
            if (!OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) {
                throw IOException("The farm vault directory is not a directory")
            }
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }
}

/**
 * Only sealed bytes reach the temporary file. Sync it before atomic same-directory replacement, then
 * sync the directory entry. Never remove the existing vault to make a rename succeed.
 *
 * Failure before replacement leaves the previous vault intact. Failure of the final directory sync
 * propagates even though the complete replacement may already be visible; callers must reconcile their
 * journal-bound pending state. Cleanup removes only this attempt's temporary file, never either vault.
 */
internal fun writeSealedVault(target: File, sealed: ByteArray, durability: VaultFileDurability) {
    val directory = requireNotNull(target.absoluteFile.parentFile)
    ensureVaultDirectory(directory, durability)
    val temporary = Files.createTempFile(directory.toPath(), target.name + ".", ".tmp")
    try {
        FileOutputStream(temporary.toFile()).use { output ->
            output.write(sealed)
            durability.syncFile(output.fd)
        }
        Files.move(temporary, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        durability.syncDirectory(directory)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private fun ensureVaultDirectory(directory: File, durability: VaultFileDurability) {
    if (directory.isDirectory) {
        // A previous mkdir may have succeeded before its parent sync failed. Retrying must
        // repeat that barrier even though the directory now exists.
        directory.parentFile?.let { durability.syncDirectory(it) }
        return
    }
    val parent = directory.parentFile ?: throw IOException("The farm vault directory is unavailable")
    ensureVaultDirectory(parent, durability)
    if (!directory.mkdir() && !directory.isDirectory) throw IOException("The farm vault directory could not be created")
    // Newly created directory entries need their own parent's metadata committed too.
    durability.syncDirectory(parent)
}
