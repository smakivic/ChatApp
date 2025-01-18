package com.example.myapplication.activities

import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.adapters.ChatAdapter
import com.example.myapplication.models.ChatMessage
import com.example.myapplication.utilities.Constants
import com.example.myapplication.utilities.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.makeramen.roundedimageview.RoundedImageView


class AnalyticsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var wordAdapter: WordAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var chatMessages: MutableList<ChatMessage>
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var imageProfile: RoundedImageView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        firestore = FirebaseFirestore.getInstance()
        recyclerView = findViewById(R.id.recyclerViewAnalytics)
        wordAdapter = WordAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = wordAdapter
        imageProfile = findViewById(R.id.imageProfile)

        val imageProfile: RoundedImageView = findViewById(R.id.imageProfile)
        imageProfile.setColorFilter(ContextCompat.getColor(this, R.color.white), PorterDuff.Mode.SRC_IN)


        imageProfile.setOnClickListener {
            onBackPressed()
        }
        loadWordAnalytics()
    }

    private fun loadWordAnalytics() {
        preferenceManager = PreferenceManager(applicationContext)
        val userId = preferenceManager.getString(Constants.KEY_USER_ID) ?: ""

        if (userId != null) {
            firestore.collection("chat")
                .whereEqualTo("senderId", userId)
                .get()
                .addOnSuccessListener { documents ->
                    val wordCount = mutableMapOf<String, Int>()
                    for (document in documents) {

                        val chatMessage = document.toObject(ChatMessage::class.java)


                        val message = chatMessage.message ?: continue
                        val words = message.split("\\s+".toRegex())


                        for (word in words) {
                            val normalizedWord = word.lowercase()
                            wordCount[normalizedWord] = wordCount.getOrDefault(normalizedWord, 0) + 1
                        }
                    }


                    val sortedWords = wordCount.toList().sortedByDescending { it.second }.take(10)
                    wordAdapter.submitList(sortedWords.map { Word(it.first, it.second) })
                }
                .addOnFailureListener { exception ->
                    exception.printStackTrace()
                }
        }
    }
}


data class Word(val word: String, val count: Int)


class WordAdapter : RecyclerView.Adapter<WordAdapter.WordViewHolder>() {
    private var wordList: List<Word> = listOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_word, parent, false)
        return WordViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val word = wordList[position]
        holder.bind(word)
    }

    override fun getItemCount(): Int = wordList.size

    fun submitList(words: List<Word>) {
        wordList = words
        notifyDataSetChanged()
    }

    inner class WordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val wordTextView: TextView = itemView.findViewById(R.id.textWord)
        private val countTextView: TextView = itemView.findViewById(R.id.textCount)

        fun bind(word: Word) {
            wordTextView.text = word.word
            countTextView.text = word.count.toString()
        }
    }
}
