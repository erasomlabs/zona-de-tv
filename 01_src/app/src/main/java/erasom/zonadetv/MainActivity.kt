package erasom.zonadetv

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import erasom.zonadetv.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var player: ExoPlayer
    private lateinit var adapter: ChannelAdapter
    private lateinit var prefs: SharedPreferences

    private val channels = mutableListOf<Channel>()

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { loadFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("zonadetv_prefs", MODE_PRIVATE)

        // Inicializar ExoPlayer
        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player

        // Inicializar RecyclerView
        adapter = ChannelAdapter(channels) { channel ->
            playChannel(channel)
        }
        binding.rvChannels.adapter = adapter
        binding.rvChannels.layoutManager = LinearLayoutManager(this)

        // Cargar última URL guardada
        val lastUrl = prefs.getString("last_url", "")
        if (!lastUrl.isNullOrEmpty()) {
            binding.etUrl.setText(lastUrl)
        }

        // Botón Cargar lista (desde URL)
        binding.btnLoad.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, R.string.url_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loadFromUrl(url)
        }

        // Botón Abrir archivo M3U
        binding.btnOpenFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        // Botón Ver tele
        binding.btnWatch.setOnClickListener {
            if (channels.isEmpty()) {
                Toast.makeText(this, R.string.no_channels, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showChannels()
        }

        // Botón Atrás
        binding.btnBack.setOnClickListener {
            showChannels()
        }
    }

    private fun loadFromUrl(urlString: String) {
        setLoading(true)

        Thread {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val inputStream: InputStream = connection.inputStream
                val reader = BufferedReader(java.io.InputStreamReader(inputStream))
                val stringBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append("\n")
                }
                reader.close()

                val parsed = M3UParser.parse(stringBuilder.toString())

                Handler(Looper.getMainLooper()).post {
                    setLoading(false)
                    if (parsed.isNotEmpty()) {
                        channels.clear()
                        channels.addAll(parsed)
                        adapter.notifyDataSetChanged()
                        prefs.edit { putString("last_url", urlString) }
                        binding.tvStatus.text = "${channels.size} canales cargados"
                        binding.tvStatus.visibility = android.view.View.VISIBLE
                        binding.btnWatch.visibility = android.view.View.VISIBLE
                    } else {
                        binding.tvStatus.text = getString(R.string.error_load)
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    setLoading(false)
                    binding.tvStatus.text = getString(R.string.error_load)
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun loadFromUri(uri: Uri) {
        setLoading(true)

        Thread {
            try {
                val inputStream: InputStream = contentResolver.openInputStream(uri)
                    ?: throw Exception("No se pudo abrir el archivo")
                val reader = BufferedReader(java.io.InputStreamReader(inputStream))
                val stringBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append("\n")
                }
                reader.close()

                val parsed = M3UParser.parse(stringBuilder.toString())

                Handler(Looper.getMainLooper()).post {
                    setLoading(false)
                    if (parsed.isNotEmpty()) {
                        channels.clear()
                        channels.addAll(parsed)
                        adapter.notifyDataSetChanged()
                        binding.tvStatus.text = "${channels.size} canales cargados"
                        binding.tvStatus.visibility = android.view.View.VISIBLE
                        binding.btnWatch.visibility = android.view.View.VISIBLE
                    } else {
                        binding.tvStatus.text = getString(R.string.error_load)
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    setLoading(false)
                    binding.tvStatus.text = getString(R.string.error_load)
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun setLoading(loading: Boolean) {
        if (loading) {
            binding.tvStatus.text = getString(R.string.loading)
            binding.tvStatus.visibility = android.view.View.VISIBLE
            binding.btnLoad.isEnabled = false
            binding.btnOpenFile.isEnabled = false
        } else {
            binding.btnLoad.isEnabled = true
            binding.btnOpenFile.isEnabled = true
        }
    }

    private fun showChannels() {
        binding.etUrl.visibility = android.view.View.GONE
        binding.btnLoad.visibility = android.view.View.GONE
        binding.btnOpenFile.visibility = android.view.View.GONE
        binding.btnWatch.visibility = android.view.View.GONE
        binding.tvStatus.visibility = android.view.View.GONE
        binding.playerView.visibility = android.view.View.GONE
        binding.btnBack.visibility = android.view.View.VISIBLE
        binding.rvChannels.visibility = android.view.View.VISIBLE
        player.stop()
    }

    private fun playChannel(channel: Channel) {
        binding.rvChannels.visibility = android.view.View.GONE
        binding.btnBack.visibility = android.view.View.VISIBLE
        binding.playerView.visibility = android.view.View.VISIBLE

        val mediaItem = MediaItem.fromUri(channel.url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        player.setPlayWhenReady(false)
    }

    override fun onResume() {
        super.onResume()
        player.setPlayWhenReady(true)
    }
}
