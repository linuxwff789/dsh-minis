package com.openminis.app

import android.content.Context
import android.util.Log
import com.openminis.app.sandbox.RootfsManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * DSH (deepseek-harness) lifecycle: installs into the proot Debian container,
 * starts the web server on 127.0.0.1:3080.
 *
 * Install runs /opt/install-dsh.sh inside the container (apt node 22 +
 * pnpm + deepseek-harness + build). Server runs /opt/start-dsh.sh in the
 * background; the WebView client connects to localhost:3080.
 */
class DshManager(private val context: Context) {

    companion object {
        private const val TAG = "DshManager"
        const val PORT = 3080

        fun getInstance(context: Context): DshManager =
            DshManager(context.applicationContext)
    }

    private val rootfsManager = RootfsManager.getInstance(context)
    private val rootfsDir: File = rootfsManager.rootfsDir
    private val prootBinary: File = rootfsManager.prootBinary
    private val logFile: File = File(context.filesDir, "dsh-install.log")

    /** /opt/.dsh-installed marker exists in the container. */
    fun isInstalled(): Boolean = File(rootfsDir, "opt/.dsh-installed").exists()

    /** True if the web server answers on 127.0.0.1:3080. */
    fun isServerRunning(): Boolean = try {
        val s = java.net.Socket("127.0.0.1", PORT)
        s.close()
        true
    } catch (_: Exception) {
        false
    }

    /** Stage install-dsh.sh into the container, then run it via proot. Returns Process (null on failure). */
    fun install(): Process? {
        if (!rootfsManager.isInstalled) {
            Log.e(TAG, "rootfs not installed yet")
            return null
        }
        val guest = File(rootfsDir, "opt/install-dsh.sh")
        guest.parentFile?.mkdirs()
        copyAsset("install-dsh.sh", guest)
        guest.setExecutable(true)

        val cmd = baseProotCmd()
        cmd.addAll(listOf("/bin/sh", "/opt/install-dsh.sh"))
        return spawn(cmd)
    }

    /** Start the DSH web server in the background. Returns Process (null on failure). */
    fun startServer(): Process? {
        if (!isInstalled()) {
            Log.e(TAG, "DSH not installed yet")
            return null
        }
        val cmd = baseProotCmd()
        cmd.addAll(listOf("/bin/sh", "/opt/start-dsh.sh"))
        return spawn(cmd)
    }

    private fun baseProotCmd(): MutableList<String> {
        val cmd = mutableListOf(prootBinary.absolutePath)
        cmd.add("-0")
        cmd.add("--link2symlink")
        cmd.add("-r"); cmd.add(rootfsDir.absolutePath)
        cmd.add("-b"); cmd.add("/dev")
        cmd.add("-b"); cmd.add("/proc")
        cmd.add("-b"); cmd.add("/sys")
        cmd.add("-w"); cmd.add("/")
        return cmd
    }

    private fun spawn(cmd: List<String>): Process? {
        try {
            val pb = ProcessBuilder(cmd)
            val env = pb.environment()
            env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            env["HOME"] = "/root"
            env["TMPDIR"] = "/tmp"
            env["PROOT_NO_SECCOMP"] = "1"
            // PROOT_LOADER if present next to proot
            val loader = File(prootBinary.parentFile, "libproot-loader.so")
            if (loader.exists()) env["PROOT_LOADER"] = loader.absolutePath
            // Keep the host PATH/LD_LIBRARY_PATH OUT of the container so no
            // termux/com.termux paths leak (TSX_TSCONFIG_PATH etc).
            env.remove("LD_LIBRARY_PATH")
            env.remove("LD_PRELOAD")
            pb.redirectErrorStream(true)
            val p = pb.start()
            // Drain output to log file.
            Thread {
                try {
                    FileOutputStream(logFile, false).use { out ->
                        p.inputStream.use { inp ->
                            val buf = ByteArray(8192)
                            while (true) {
                                val n = inp.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }.start()
            return p
        } catch (e: Exception) {
            Log.e(TAG, "spawn failed: ${cmd[0]}", e)
            return null
        }
    }

    private fun copyAsset(asset: String, dest: File) {
        try {
            context.assets.open(asset).use { inp ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(65536)
                    var n: Int
                    while (inp.read(buf).also { n = it } != -1) out.write(buf, 0, n)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "copy asset failed: $asset", e)
        }
    }
}
