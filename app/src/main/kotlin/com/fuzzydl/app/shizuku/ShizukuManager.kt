package com.fuzzydl.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import com.fuzzydl.app.BuildConfig
import com.fuzzydl.app.data.AppLog
import com.fuzzydl.app.data.ShizukuStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Fluxo completo: não instalado → parado → sem permissão → pronto.
 * Em Android ≤ 10 não é necessário: o app escreve direto (legacy storage).
 */
object ShizukuManager {
    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQ_PERMISSION = 4801

    private lateinit var context: Context
    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val _serviceReady = MutableStateFlow(false)
    val serviceReady: StateFlow<Boolean> = _serviceReady.asStateFlow()

    @Volatile
    private var service: IFileService? = null

    @Volatile
    private var binding = false

    @Volatile
    private var serviceArgs: Shizuku.UserServiceArgs? = null

    @Volatile
    private var registered = false

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        _serviceReady.value = false
        refresh()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null) IFileService.Stub.asInterface(binder) else null
            _serviceReady.value = service != null
            binding = false
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _serviceReady.value = false
            binding = false
            refresh()
        }
    }

    fun init(ctx: Context) {
        context = ctx.applicationContext
        if (!registered) {
            runCatching {
                Shizuku.addBinderReceivedListenerSticky(binderReceived)
                Shizuku.addBinderDeadListener(binderDead)
                Shizuku.addRequestPermissionResultListener(permissionResult)
            }
            registered = true
        }
        refresh()
    }

    fun refresh() {
        val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        val installed = isInstalled()
        val running = !legacy && runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permission = if (legacy || !running) legacy else runCatching {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val version = if (running) runCatching { Shizuku.getVersion() }.getOrDefault(0) else 0
        val ready = legacy || (installed && running && permission)
        _status.value = ShizukuStatus(
            installed = installed || legacy,
            running = running || legacy,
            permission = permission,
            legacy = legacy,
            ready = ready,
            version = version,
            error = if (!legacy && installed && !running) "Shizuku instalado, mas parado" else null,
        )
    }

    private fun isInstalled(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE, 0)
        }
        true
    }.getOrDefault(false)

    fun requestPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(REQ_PERMISSION)
            }
        }.onFailure { AppLog.e("Shizuku", "requestPermission", it) }
    }

    fun shouldShowRationale(): Boolean = runCatching {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Shizuku.shouldShowRequestPermissionRationale()
    }.getOrDefault(false)

    fun connect() {
        val s = _status.value
        if (s.legacy || !s.ready || service != null || binding) return
        val component = ComponentName(context.packageName, FileService::class.java.name)
        val args = Shizuku.UserServiceArgs(component)
            .daemon(false)
            .processNameSuffix("fs")
            .version(BuildConfig.VERSION_CODE)
            .debuggable(BuildConfig.DEBUG)
        binding = true
        serviceArgs = args
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure {
                binding = false
                serviceArgs = null
                AppLog.e("Shizuku", "bindUserService", it)
            }
    }

    fun disconnect() {
        val args = serviceArgs
        if (args != null && (binding || service != null)) {
            runCatching { service?.destroy() }
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
        service = null
        serviceArgs = null
        _serviceReady.value = false
        binding = false
    }

    fun serviceOrNull(): IFileService? = service

    suspend fun awaitService(timeoutMs: Long = 20_000): IFileService? {
        if (_status.value.legacy) return null
        val current = service
        if (current != null) return current
        connect()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            service?.let { return it }
            if (!_status.value.ready) return null
            delay(100)
        }
        return service
    }

    fun openShizuku(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launch) }
                .onFailure { openUrl(context, "https://github.com/RikkaApps/Shizuku") }
        } else {
            openUrl(context, "https://github.com/RikkaApps/Shizuku")
        }
    }

    fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun release() {
        disconnect()
        if (registered) {
            runCatching {
                Shizuku.removeBinderReceivedListener(binderReceived)
                Shizuku.removeBinderDeadListener(binderDead)
                Shizuku.removeRequestPermissionResultListener(permissionResult)
            }
            registered = false
        }
    }
}
