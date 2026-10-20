package com.iptvplayer.app.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * Rzucanie strumienia z telefonu na appkę VivaPilot działającą na TV, w tej samej sieci
 * Wi-Fi ("Chcę rzucać obraz z appki na telefonie NA appkę na TV, obie mają być tą samą
 * appką"). CELOWO NIE Google Cast — to wymagałoby rejestracji własnego odbiornika w Google
 * (osobna, długa procedura, appka WWW jako "odbiornik", zewnętrzna zależność) tylko po to,
 * żeby połączyć dwie instancje TEJ SAMEJ appki. Zamiast tego: własny, prosty protokół nad
 * zwykłym gniazdem TCP (jedna linia tekstu, pola rozdzielone znakiem "|") + wykrywanie przez
 * NsdManager (mDNS/Bonjour) — to standardowe, długo obecne API Androida (od API 16), nie
 * wymaga żadnej usługi zewnętrznej, działa WYŁĄCZNIE w obrębie tej samej sieci lokalnej.
 *
 * TV: ogłasza się w sieci (registerService) i nasłuchuje na gnieździe (ServerSocket) —
 * patrz startTvReceiver.
 * Telefon: wyszukuje ogłoszone instancje (discoverServices) i wysyła do wybranej jedną
 * linię z danymi materiału do odtworzenia — patrz discoverTvTargets/sendCast.
 */
object CastManager {
    private const val TAG = "CastManager"
    private const val SERVICE_TYPE = "_vivapilot._tcp."
    private const val SERVICE_NAME = "VivaPilot"
    private const val PORT = 58413

    /** Jeden wykryty odbiornik (instancja appki na TV) widoczny na liście do wyboru. */
    data class CastTarget(val name: String, val host: String, val port: Int)

    /** Dane materiału wysyłane do odtworzenia na TV. */
    data class CastPayload(
        val streamUrl: String,
        val title: String,
        val userAgent: String?,
        val referer: String?,
        val positionMs: Long,
    )

    // === Strona TV: ogłoszenie w sieci + nasłuch przychodzących rzutów ===

    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    /** Uruchamia na TV nasłuch na rzuty z telefonu — wołane raz przy starcie appki, TYLKO
     * na urządzeniach TV (patrz IptvApp.kt). `onCastReceived` jest wołane w tle (NIE na
     * głównym wątku) za każdym razem, gdy telefon coś wyśle — appka wywołująca musi sama
     * przełączyć się na główny wątek przed uruchomieniem odtwarzacza. */
    fun startTvReceiver(context: Context, onCastReceived: (CastPayload) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = ServerSocket(PORT)
                serverSocket = socket
                registerNsdService(context, socket.localPort)
                while (true) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    handleIncomingCast(client, onCastReceived)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Nie udało się uruchomić odbiornika rzutowania: ${e.message}")
            }
        }
    }

    private fun handleIncomingCast(client: Socket, onCastReceived: (CastPayload) -> Unit) {
        try {
            client.use {
                val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                val line = reader.readLine() ?: return
                val parts = line.split("|")
                if (parts.size >= 5) {
                    onCastReceived(
                        CastPayload(
                            streamUrl = parts[0],
                            title = parts[1],
                            userAgent = parts[2].takeIf { s -> s.isNotEmpty() },
                            referer = parts[3].takeIf { s -> s.isNotEmpty() },
                            positionMs = parts[4].toLongOrNull() ?: 0L,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Błąd odczytu przychodzącego rzutu: ${e.message}")
        }
    }

    private fun registerNsdService(context: Context, port: Int) {
        runCatching {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = SERVICE_NAME
                serviceType = SERVICE_TYPE
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    Log.i(TAG, "Ogłoszono usługę rzutowania: ${info.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Nie udało się ogłosić usługi rzutowania: $errorCode")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            }
            registrationListener = listener
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    // === Strona telefonu: wykrywanie dostępnych TV + wysyłanie rzutu ===

    /** Rozpoczyna wyszukiwanie instancji appki na TV w tej samej sieci. Zwraca listener,
     * który trzeba przekazać do stopDiscovery(), gdy ekran/dialog wyboru zostanie zamknięty
     * — bez tego wyszukiwanie zostałoby uruchomione bez końca w tle. `onFound` może zostać
     * wywołane wielokrotnie dla tego samego urządzenia (aktualizacja adresu) — appka
     * wywołująca powinna nadpisywać po nazwie, nie dodawać duplikatów. */
    fun discoverTvTargets(
        context: Context,
        onFound: (CastTarget) -> Unit,
        onLost: (String) -> Unit,
    ): NsdManager.DiscoveryListener {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Nie udało się rozpocząć wyszukiwania TV: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {
                onLost(info.serviceName)
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType != SERVICE_TYPE && !info.serviceType.startsWith("_vivapilot")) return
                runCatching {
                    nsdManager.resolveService(
                        info,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val host = serviceInfo.host?.hostAddress ?: return
                                onFound(CastTarget(serviceInfo.serviceName, host, serviceInfo.port))
                            }
                        },
                    )
                }
            }
        }
        runCatching { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
        return discoveryListener
    }

    fun stopDiscovery(context: Context, listener: NsdManager.DiscoveryListener) {
        runCatching {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager.stopServiceDiscovery(listener)
        }
    }

    /** Wysyła materiał do odtworzenia na wybrany, wcześniej wykryty TV. `onResult` wołane
     * w tle (NIE na głównym wątku) z true/false w zależności od powodzenia połączenia. */
    fun sendCast(target: CastTarget, payload: CastPayload, onResult: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = runCatching {
                Socket(target.host, target.port).use { socket ->
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    val line = listOf(
                        payload.streamUrl,
                        payload.title,
                        payload.userAgent.orEmpty(),
                        payload.referer.orEmpty(),
                        payload.positionMs.toString(),
                    ).joinToString("|") { it.replace("|", " ").replace("\n", " ") }
                    writer.println(line)
                }
            }.isSuccess
            onResult(success)
        }
    }
}
