package com.example.audiobpmeditor

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.audiobpmeditor.databinding.ActivityMainBinding
import com.masoudss.lib.WaveformSeekBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var engine: AudioEngine

    private var loadedUri: Uri? = null
    private var baseBpm: Float = 120f
    private var currentBpm: Int = 120
    private var durationMs: Long = 0L

    // Edit segments to keep (start,end ms)
    private val keptSegments = mutableListOf<Pair<Long,Long>>()

    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressJob: Job? = null

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadAudio(it) }
    }

    private val requestPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        engine = AudioEngine(this)
        engine.init()

        engine.onProgress = { pos, dur ->
            runOnUiThread {
                binding.waveform.progress = if (dur>0) (pos.toFloat()/dur*1000f) else 0f
                binding.tvTime.text = "${formatMs(pos)} / ${formatMs(dur)}"
                // sync cut timeline marker
                if (binding.cutTimeline.markerMs != pos) {
                    binding.cutTimeline.markerMs = pos
                    binding.tvMarker.text = "marker: ${formatMsFull(pos)}"
                }
            }
        }
        engine.onEnded = {
            runOnUiThread { binding.btnPlay.isEnabled = true }
        }

        setupUi()
        startProgressLoop()
    }

    private fun setupUi() {
        binding.btnLoad.setOnClickListener { checkPermAndPick() }
        binding.sliderBpm.addOnChangeListener { _, value, _ ->
            currentBpm = value.roundToInt()
            binding.etBpm.setText(currentBpm.toString())
            updateSpeed()
        }
        binding.etBpm.setOnEditorActionListener { v, _, _ ->
            v.text.toString().toIntOrNull()?.let {
                val b = it.coerceIn(1,999)
                currentBpm = b
                binding.sliderBpm.value = b.toFloat()
                updateSpeed()
            }
            true
        }
        binding.sliderPitch.addOnChangeListener { _, value, _ ->
            binding.etPitch.setText(String.format("%.1f", value))
            engine.setPitchSemitones(value)
        }
        binding.etPitch.setOnEditorActionListener { v, _, _ ->
            v.text.toString().toFloatOrNull()?.let {
                val p = it.coerceIn(-12f,12f)
                binding.sliderPitch.value = p
                engine.setPitchSemitones(p)
            }
            true
        }
        binding.swReverse.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(this, if(isChecked) "Reverse ON – verrà applicato in export" else "Reverse OFF", Toast.LENGTH_SHORT).show()
        }
        binding.swLoop.setOnCheckedChangeListener { _, isChecked ->
            engine.setLooping(isChecked)
        }

        binding.btnPlay.setOnClickListener { engine.play() }
        binding.btnPause.setOnClickListener { engine.pause() }
        binding.btnStop.setOnClickListener { engine.stop() }

        // Cut timeline
        binding.cutTimeline.onMarkerChanged = { ms ->
            binding.tvMarker.text = "marker: ${formatMsFull(ms)}"
            engine.seekTo(ms)
        }
        binding.cutTimeline.onSelectionChanged = { s,e ->
            binding.tvSelection.text = "Selezionato: ${formatMsFull(s)} → ${formatMsFull(e)}  (${formatMsFull(e-s)})"
        }

        binding.btnCut.setOnClickListener {
            if (binding.cutTimeline.addCutAtMarker()) {
                Toast.makeText(this, "Cut @ ${formatMsFull(binding.cutTimeline.markerMs)}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnErase.setOnClickListener {
            val seg = binding.cutTimeline.eraseSelected()
            if (seg != null) {
                // mark segment to remove
                keptSegments.clear() // we'll rebuild on export
                binding.tvSelection.text = "Rimosso segmento ${formatMsFull(seg.first)}–${formatMsFull(seg.second)} (verrà escluso in export)"
                Toast.makeText(this, "Segmento selezionato per eliminazione", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Doppio tap su un segmento per selezionarlo", Toast.LENGTH_SHORT).show()
            }
        }

        // keyboard navigation for timeline
        binding.cutTimeline.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { binding.cutTimeline.stepMarker(-1); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { binding.cutTimeline.stepMarker(1); true }
                    else -> false
                }
            } else false
        }

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_clear))
                .setPositiveButton(R.string.yes) { _,_ -> resetAll() }
                .setNegativeButton(R.string.no, null)
                .show()
        }

        binding.btnExport.setOnClickListener {
            exportMp3()
        }

        // waveform seek
        binding.waveform.onProgressChanged = object: WaveformSeekBar.OnProgressChanged {
            override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                if (fromUser && durationMs > 0) {
                    val ms = (progress/1000f * durationMs).toLong()
                    engine.seekTo(ms)
                    binding.cutTimeline.markerMs = ms
                }
            }
        }
    }

    private fun checkPermAndPick() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPerm.launch(Manifest.permission.READ_MEDIA_AUDIO)
                return
            }
        }
        pickAudio.launch("audio/*")
    }

    private fun loadAudio(uri: Uri) {
        loadedUri = uri
        engine.load(uri, binding.swReverse.isChecked)
        // file name
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
        binding.tvFileName.text = name

        // detect duration
        lifecycleScope.launch(Dispatchers.IO) {
            val dur = getAudioDuration(uri)
            durationMs = dur
            withContext(Dispatchers.Main) {
                binding.tvTime.text = "00:00 / ${formatMs(dur)}"
                binding.cutTimeline.durationMs = dur
                binding.cutTimeline.markerMs = 0
                binding.cutTimeline.cuts.clear()
                binding.cutTimeline.invalidate()
            }
            // load waveform
            try {
                withContext(Dispatchers.Main) {
                    binding.waveform.setSampleFrom(uri)
                }
            } catch (_: Exception) {}
        }

        // reset segments
        keptSegments.clear()
        binding.tvStatus.text = "File caricato. BPM base: $baseBpm"
        binding.tvSelection.text = "Nessuna selezione – doppio tap su segmento per selezionare"
    }

    private fun getAudioDuration(uri: Uri): Long {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(this, uri, null)
            val format = extractor.getTrackFormat(0)
            val durUs = format.getLong(MediaFormat.KEY_DURATION)
            extractor.release()
            durUs / 1000
        } catch (_: Exception) { 180_000L }
    }

    private fun updateSpeed() {
        engine.setBpm(currentBpm, baseBpm)
        val ratio = currentBpm / baseBpm
        binding.tvSpeedRatio.text = "x%.2f".format(ratio)
        binding.tvStatus.text = "Speed: ${"%.2f".format(ratio)} – Sonic time-stretch, pitch preservato"
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                engine.pollProgress()
                delay(60)
            }
        }
    }

    private fun exportMp3() {
        val uri = loadedUri
        if (uri == null) {
            Toast.makeText(this, "Carica un file prima", Toast.LENGTH_SHORT).show()
            return
        }
        binding.tvStatus.text = "Export in corso..."
        binding.btnExport.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Build keep segments from cut timeline (exclude selected erased)
                val allSegs = binding.cutTimeline.getSegments()
                val selectedIdx = binding.cutTimeline.selectedSegmentIndex
                val keep = allSegs.filterIndexed { idx, _ -> idx != selectedIdx }
                val finalKeep = if (keep.isEmpty()) listOf(0L to durationMs) else keep

                val exporter = AudioExportHelper(this@MainActivity)
                val reverse = binding.swReverse.isChecked
                val pitchSt = binding.sliderPitch.value
                val speedRatio = currentBpm / baseBpm

                val outFile = File(getExternalFilesDir(null), "AudioBpmEditor_${System.currentTimeMillis()}.mp3")
                val ok = exporter.export(
                    inputUri = uri,
                    outputFile = outFile,
                    keepSegments = finalKeep,
                    speedRatio = speedRatio,
                    pitchSemitones = pitchSt,
                    reverse = reverse
                )
                withContext(Dispatchers.Main) {
                    binding.btnExport.isEnabled = true
                    if (ok) {
                        binding.tvStatus.text = "Esportato: ${outFile.absolutePath}  (${outFile.length()/1024} KB)"
                        Toast.makeText(this@MainActivity, "MP3 salvato!", Toast.LENGTH_LONG).show()
                        // share
                        AudioExportHelper.shareFile(this@MainActivity, outFile)
                    } else {
                        binding.tvStatus.text = "Export fallito – vedi log"
                        Toast.makeText(this@MainActivity, "Export fallito", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnExport.isEnabled = true
                    binding.tvStatus.text = "Errore: ${e.message}"
                    Toast.makeText(this@MainActivity, "Errore: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun resetAll() {
        engine.stop()
        loadedUri = null
        durationMs = 0
        binding.tvFileName.text = getString(R.string.no_file)
        binding.tvTime.text = "00:00 / 00:00"
        binding.waveform.progress = 0f
        try { binding.waveform.setSampleFrom(intArrayOf()) } catch (_:Exception){}
        binding.cutTimeline.clearAll()
        binding.etBpm.setText("120")
        binding.sliderBpm.value = 120f
        binding.etPitch.setText("0.0")
        binding.sliderPitch.value = 0f
        binding.swReverse.isChecked = false
        binding.swLoop.isChecked = false
        currentBpm = 120
        baseBpm = 120f
        engine.setBaseBpm(baseBpm)
        updateSpeed()
        binding.tvStatus.text = "Pronto."
        binding.tvSelection.text = "Nessuna selezione – doppio tap su segmento per selezionare"
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
        engine.release()
    }

    private fun formatMs(ms: Long): String {
        val s = ms/1000
        return String.format("%02d:%02d", s/60, s%60)
    }
    private fun formatMsFull(ms: Long): String {
        val s = ms/1000
        val mss = ms%1000
        return String.format("%02d:%02d.%03d", s/60, s%60, mss)
    }
}
