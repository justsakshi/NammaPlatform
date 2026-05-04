package com.example.nammaplatform

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeech
    private var isKannada = true

    private lateinit var titleText: TextView
    private lateinit var stationLabel: TextView
    private lateinit var nextTrainsLabel: TextView
    private lateinit var helpButton: Button
    private lateinit var langToggle: Button
    private lateinit var stationInput: AutoCompleteTextView

    private lateinit var train1Name: TextView
    private lateinit var train1Platform: TextView
    private lateinit var coachStrip1: LinearLayout

    private lateinit var train2Name: TextView
    private lateinit var train2Platform: TextView
    private lateinit var coachStrip2: LinearLayout

    private lateinit var train3Name: TextView
    private lateinit var train3Platform: TextView
    private lateinit var coachStrip3: LinearLayout

    data class TrainInfo(
        val nameKn: String, val nameEn: String,
        val platformKn: String, val platformEn: String,
        val coaches: List<String>
    )

    data class StationData(
        val trains: List<TrainInfo>,
        val ttsKn: String,
        val ttsEn: String
    )

    private val stationDatabase = mutableMapOf<String, StationData>()
    private val stationNameMap  = mutableMapOf<String, String>()
    private var currentStationKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        loadStationsFromJson()
        setupAutocomplete()
        setupTts()
        updateUI()

        langToggle.setOnClickListener {
            isKannada = !isKannada
            tts.language = if (isKannada) Locale("kn", "IN") else Locale.ENGLISH
            updateUI()
            // Re-render cards so coach labels (GEN/ಜನರಲ್, LADIES/ಮಹಿಳೆ) update too
            currentStationKey?.let { key ->
                stationDatabase[key]?.let { renderTrainCards(it) }
            }
        }

        helpButton.setOnClickListener { speak() }
    }

    private fun bindViews() {
        titleText       = findViewById(R.id.title)
        stationLabel    = findViewById(R.id.stationLabel)
        nextTrainsLabel = findViewById(R.id.nextTrainsLabel)
        helpButton      = findViewById(R.id.helpButton)
        langToggle      = findViewById(R.id.langToggle)
        stationInput    = findViewById(R.id.stationInput)

        train1Name     = findViewById(R.id.train1Name)
        train1Platform = findViewById(R.id.train1Platform)
        coachStrip1    = findViewById(R.id.coachStrip1)

        train2Name     = findViewById(R.id.train2Name)
        train2Platform = findViewById(R.id.train2Platform)
        coachStrip2    = findViewById(R.id.coachStrip2)

        train3Name     = findViewById(R.id.train3Name)
        train3Platform = findViewById(R.id.train3Platform)
        coachStrip3    = findViewById(R.id.coachStrip3)
    }

    private fun loadStationsFromJson() {
        try {
            val inputStream = resources.openRawResource(R.raw.stations)
            val jsonText = inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonText)
            val stationsArray = root.getJSONArray("stations")

            for (i in 0 until stationsArray.length()) {
                val s = stationsArray.getJSONObject(i)
                val key = s.getString("key")

                val namesArr = s.getJSONArray("displayNames")
                for (j in 0 until namesArr.length()) {
                    stationNameMap[namesArr.getString(j)] = key
                }

                val trainsArr = s.getJSONArray("trains")
                val trains = mutableListOf<TrainInfo>()
                for (t in 0 until trainsArr.length()) {
                    val tr = trainsArr.getJSONObject(t)
                    val coachArr = tr.getJSONArray("coaches")
                    val coaches = (0 until coachArr.length()).map { coachArr.getString(it) }
                    trains.add(TrainInfo(
                        nameKn     = tr.getString("nameKn"),
                        nameEn     = tr.getString("nameEn"),
                        platformKn = tr.getString("platformKn"),
                        platformEn = tr.getString("platformEn"),
                        coaches    = coaches
                    ))
                }

                stationDatabase[key] = StationData(
                    trains = trains,
                    ttsKn  = s.getString("ttsKn"),
                    ttsEn  = s.getString("ttsEn")
                )
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load station data", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupAutocomplete() {
        val displayNames = stationNameMap.keys.sorted()
        val autoAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayNames)
        stationInput.setAdapter(autoAdapter)

        stationInput.setOnItemClickListener { _, _, _, _ ->
            loadStation(stationInput.text.toString().trim())
        }
        stationInput.setOnEditorActionListener { _, _, _ ->
            loadStation(stationInput.text.toString().trim()); false
        }
    }

    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale("kn", "IN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "Kannada TTS not available, using English", Toast.LENGTH_SHORT).show()
                    tts.language = Locale.ENGLISH
                    isKannada = false
                    updateUI()
                }
            }
        }
    }

    private fun loadStation(input: String) {
        val key = stationNameMap[input]
        if (key != null && stationDatabase.containsKey(key)) {
            currentStationKey = key
            renderTrainCards(stationDatabase[key]!!)
        } else {
            Toast.makeText(this,
                if (isKannada) "ಸ್ಟೇಶನ್ ಕಂಡುಬಂದಿಲ್ಲ" else "Station not found",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderTrainCards(data: StationData) {
        val nameFlds     = listOf(train1Name,     train2Name,     train3Name)
        val platformFlds = listOf(train1Platform, train2Platform, train3Platform)
        val strips       = listOf(coachStrip1,    coachStrip2,    coachStrip3)

        for (i in 0..2) {
            if (i < data.trains.size) {
                val t = data.trains[i]
                nameFlds[i].text     = if (isKannada) t.nameKn     else t.nameEn
                platformFlds[i].text = if (isKannada) t.platformKn else t.platformEn
                buildCoachStrip(strips[i], t.coaches, isKannada)
            } else {
                nameFlds[i].text     = "—"
                platformFlds[i].text = ""
                strips[i].removeAllViews()
            }
        }
    }

    private fun buildCoachStrip(container: LinearLayout, coaches: List<String>, kannada: Boolean) {
        container.removeAllViews()

        val dp4  = (4  * resources.displayMetrics.density).toInt()
        val dp40 = (40 * resources.displayMetrics.density).toInt()
        val dp56 = (56 * resources.displayMetrics.density).toInt()

        for (coach in coaches) {
            val tv = TextView(this)
            val params = LinearLayout.LayoutParams(dp56, dp40)
            params.setMargins(0, 0, dp4, 0)
            tv.layoutParams = params
            tv.gravity = Gravity.CENTER
            tv.textSize = 11f
            tv.setTypeface(null, Typeface.BOLD)
            tv.setPadding(dp4, dp4, dp4, dp4)

            when (coach) {
                "ENGINE" -> {
                    tv.text = "🚆"
                    tv.textSize = 20f
                    tv.setBackgroundColor(Color.parseColor("#37474F"))
                    tv.setTextColor(Color.WHITE)
                }
                "GEN", "GENERAL", "UR", "GS" -> {
                    tv.text = if (kannada) "ಜನರಲ್" else "GEN"
                    tv.setBackgroundColor(Color.parseColor("#FFD600"))
                    tv.setTextColor(Color.parseColor("#0D47A1"))
                }
                "LADIES" -> {
                    tv.text = if (kannada) "ಮಹಿಳೆ" else "LADIES"
                    tv.setBackgroundColor(Color.parseColor("#E91E63"))
                    tv.setTextColor(Color.WHITE)
                }
                "1A" -> {
                    tv.text = "1A"
                    tv.setBackgroundColor(Color.parseColor("#6A1B9A"))  // deep purple
                    tv.setTextColor(Color.WHITE)
                }
                "2A" -> {
                    tv.text = "2A"
                    tv.setBackgroundColor(Color.parseColor("#1565C0"))  // deep blue
                    tv.setTextColor(Color.WHITE)
                }
                "3A" -> {
                    tv.text = "3A"
                    tv.setBackgroundColor(Color.parseColor("#0288D1"))  // light blue
                    tv.setTextColor(Color.WHITE)
                }
                "3E" -> {
                    tv.text = "3E"
                    tv.setBackgroundColor(Color.parseColor("#0097A7"))  // teal
                    tv.setTextColor(Color.WHITE)
                }
                "CC" -> {
                    tv.text = "CC"
                    tv.setBackgroundColor(Color.parseColor("#00796B"))  // teal green
                    tv.setTextColor(Color.WHITE)
                }
                "EC" -> {
                    tv.text = "EC"
                    tv.setBackgroundColor(Color.parseColor("#2E7D32"))  // dark green
                    tv.setTextColor(Color.WHITE)
                }
                "2S" -> {
                    tv.text = "2S"
                    tv.setBackgroundColor(Color.parseColor("#F57F17"))  // amber
                    tv.setTextColor(Color.WHITE)
                }
                "SLR", "LRD" -> {
                    tv.text = coach
                    tv.setBackgroundColor(Color.parseColor("#546E7A"))  // blue grey
                    tv.setTextColor(Color.WHITE)
                }
                "PC" -> {
                    tv.text = if (kannada) "ಊಟ" else "PC"
                    tv.setBackgroundColor(Color.parseColor("#BF360C"))  // deep orange
                    tv.setTextColor(Color.WHITE)
                }
                "EOG" -> {
                    tv.text = "EOG"
                    tv.setBackgroundColor(Color.parseColor("#4E342E"))  // brown
                    tv.setTextColor(Color.WHITE)
                }
                "VISTA" -> {
                    tv.text = if (kannada) "ವಿಸ್ಟಾ" else "VISTA"
                    tv.setBackgroundColor(Color.parseColor("#00838F"))
                    tv.setTextColor(Color.WHITE)
                }
                else -> {
                    // S1-S12, B1-B4, A1-A2 — Sleeper/Berth coaches
                    tv.text = coach
                    when {
                        coach.startsWith("S") -> tv.setBackgroundColor(Color.parseColor("#1565C0"))
                        coach.startsWith("B") -> tv.setBackgroundColor(Color.parseColor("#6A1B9A"))
                        coach.startsWith("A") -> tv.setBackgroundColor(Color.parseColor("#4A148C"))
                        else                  -> tv.setBackgroundColor(Color.parseColor("#0D47A1"))
                    }
                    tv.setTextColor(Color.WHITE)
                }
            }
            container.addView(tv)
        }
    }
    private fun updateUI() {
        if (isKannada) {
            langToggle.text      = "EN"
            titleText.text       = "ನಮ್ಮ ಪ್ಲಾಟ್‌ಫಾರ್ಮ್"
            stationLabel.text    = "ನಿಮ್ಮ ಸ್ಟೇಶನ್ ಹೆಸರು ಟೈಪ್ ಮಾಡಿ"
            nextTrainsLabel.text = "ಮುಂದಿನ 3 ರೈಲುಗಳು"
            helpButton.text      = "ಸಹಾಯ ಮಾಡಿ"
        } else {
            langToggle.text      = "ಕನ್ನಡ"
            titleText.text       = "Namma Platform"
            stationLabel.text    = "Type your station name"
            nextTrainsLabel.text = "Next 3 Trains"
            helpButton.text      = "HELP ME"
        }
    }

    private fun speak() {
        val key = currentStationKey
        val message = if (key != null && stationDatabase.containsKey(key)) {
            if (isKannada) stationDatabase[key]!!.ttsKn else stationDatabase[key]!!.ttsEn
        } else {
            if (isKannada) "ದಯವಿಟ್ಟು ಮೊದಲು ಒಂದು ಸ್ಟೇಶನ್ ಆಯ್ಕೆ ಮಾಡಿ."
            else "Please select a station first."
        }
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }
}