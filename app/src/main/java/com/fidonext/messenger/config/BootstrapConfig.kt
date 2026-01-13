package com.fidonext.messenger.config

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads and manages bootstrap node configuration
 */
object BootstrapConfig {

    private const val BOOTSTRAP_FILE = "bootstrap_nodes.txt"

    /**
     * Load bootstrap peers from assets
     */
    fun loadBootstrapPeers(context: Context): Array<String> {
        val peers = mutableListOf<String>()

        try {
            context.assets.open(BOOTSTRAP_FILE).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .forEach { peers.add(it) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BootstrapConfig", "Failed to load bootstrap peers", e)
        }

        return peers.toTypedArray()
    }

    /**
     * Get default bootstrap peers if file not found
     */
    fun getDefaultBootstrapPeers(): Array<String> {
        return arrayOf(
            "/ip4/10.0.0.10/tcp/41000/p2p/12D3KooWSNqRTV2JccvWYTxtDtXUk9Y1m7EcGLy65eRN1qSrDkdp"
        )
    }
}
