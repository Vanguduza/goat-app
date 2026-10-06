package com.farmos.app

import com.farmos.domain.replication.FarmDiscoveryDescriptor
import java.io.Closeable
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Stand-in for the Android Keystore sealer, which the JVM test runtime does not provide. */
internal class TestSoftwareSealer : DeviceSealer {
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)

    override fun seal(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        return iv + Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) }.doFinal(plaintext)
    }

    override fun open(sealed: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed.copyOfRange(0, 12))) }
            .doFinal(sealed.copyOfRange(12, sealed.size))
}

/** In-memory stand-in for NSD: farms are "found" exactly as advertised, on the loopback interface. */
internal class TestFarmNetwork(private val host: String) : FarmPeerDiscovery {
    private val advertised = linkedMapOf<String, DiscoveredFarm>()
    private val listeners = mutableListOf<(List<DiscoveredFarm>) -> Unit>()

    @Synchronized
    fun farms(): List<DiscoveredFarm> = advertised.values.toList()

    @Synchronized
    override fun advertise(serviceName: String, descriptor: FarmDiscoveryDescriptor, port: Int): Closeable {
        advertised[serviceName] = DiscoveredFarm(FarmDiscoveryDescriptor.fromTxtRecord(descriptor.toTxtRecord())!!, host, port, serviceName)
        notifyListeners()
        return Closeable {
            synchronized(this) {
                advertised.remove(serviceName)
                notifyListeners()
            }
        }
    }

    @Synchronized
    override fun discover(onChange: (List<DiscoveredFarm>) -> Unit): Closeable {
        listeners += onChange
        onChange(advertised.values.toList())
        return Closeable { synchronized(this) { listeners -= onChange } }
    }

    private fun notifyListeners() {
        val farms = advertised.values.toList()
        listeners.toList().forEach { it(farms) }
    }
}
