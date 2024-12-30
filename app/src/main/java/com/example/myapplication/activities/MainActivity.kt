package com.example.myapplication.activities

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.utilities.Constants
import com.example.myapplication.utilities.PreferenceManager
import android.util.Base64
import android.view.View
import com.example.myapplication.adapters.RecentConversationsAdapter
import com.example.myapplication.listeners.ConversationListener
import com.example.myapplication.models.ChatMessage
import com.example.myapplication.models.User
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity(), ConversationListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var conversations: MutableList<ChatMessage>
    private lateinit var conversationsAdapter: RecentConversationsAdapter
    private lateinit var database: FirebaseFirestore


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        setContentView(R.layout.activity_main)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferenceManager = PreferenceManager(applicationContext)

        init()
        loadUserDetails()
        getToken()
        setListeners()
        listenConversations()
    }

    private fun init() {
        conversations = mutableListOf()
        conversationsAdapter = RecentConversationsAdapter(conversations, this)
        binding.conversationsRecyclerView.adapter = conversationsAdapter
        database = FirebaseFirestore.getInstance()
    }


    private fun setListeners() {
        binding.imageSignOut.setOnClickListener{ _ -> signOut()}
        binding.fabNewChat.setOnClickListener{v ->
            startActivity(Intent(applicationContext, UsersActivity::class.java))
        }
    }

    private fun loadUserDetails(){
        binding.textName.text = preferenceManager.getString(Constants.KEY_NAME)
        val bytes = Base64.decode(preferenceManager.getString(Constants.KEY_IMAGE),
            Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        binding.imageProfile.setImageBitmap(bitmap)

    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun listenConversations() {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
            .addSnapshotListener(eventListener)
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
            .addSnapshotListener(eventListener)
    }

    private val eventListener = EventListener<QuerySnapshot> { value, error ->
        if (error != null) {
            return@EventListener
        }
        if (value != null) {
            for (documentChange in value.documentChanges) {
                when (documentChange.type) {
                    DocumentChange.Type.ADDED -> {
                        val senderId = documentChange.document.getString(Constants.KEY_SENDER_ID) ?: ""
                        val receiverId = documentChange.document.getString(Constants.KEY_RECEIVER_ID) ?: ""
                        val chatMessage = ChatMessage().apply {
                            this.senderId = senderId
                            this.receiverId = receiverId
                            if (preferenceManager.getString(Constants.KEY_USER_ID) == senderId) {
                                this.conversationImage = documentChange.document.getString(Constants.KEY_RECEIVER_IMAGE) ?: ""
                                this.conversationName = documentChange.document.getString(Constants.KEY_RECEIVER_NAME) ?: ""
                                this.conversationId = documentChange.document.getString(Constants.KEY_RECEIVER_ID) ?: ""
                            } else {
                                this.conversationImage = documentChange.document.getString(Constants.KEY_SENDER_IMAGE) ?: ""
                                this.conversationName = documentChange.document.getString(Constants.KEY_SENDER_NAME) ?: ""
                                this.conversationId = documentChange.document.getString(Constants.KEY_SENDER_ID) ?: ""
                            }
                            this.message = documentChange.document.getString(Constants.KEY_LAST_MESSAGE) ?: ""
                            this.dateObject = documentChange.document.getDate(Constants.KEY_TIMESTAMP)
                        }
                        conversations.add(chatMessage)
                    }

                    DocumentChange.Type.MODIFIED -> {
                        for (i in conversations.indices) {
                            val senderId = documentChange.document.getString(Constants.KEY_SENDER_ID) ?: ""
                            val receiverId = documentChange.document.getString(Constants.KEY_RECEIVER_ID) ?: ""
                            if (conversations[i].senderId == senderId && conversations[i].receiverId == receiverId) {
                                conversations[i].apply {
                                    this.message = documentChange.document.getString(Constants.KEY_LAST_MESSAGE) ?: ""
                                    this.dateObject = documentChange.document.getDate(Constants.KEY_TIMESTAMP)
                                }
                                break
                            }
                        }
                    }

                    else -> Unit
                }
            }
            conversations.sortByDescending { it.dateObject }
            conversationsAdapter.notifyDataSetChanged()
            binding.conversationsRecyclerView.smoothScrollToPosition(0)
            binding.conversationsRecyclerView.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
        }
    }


    private fun getToken(){
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            updateToken(token)
        }
    }

    private fun updateToken(token: String){
        val database = FirebaseFirestore.getInstance()
        val documentReference = database.collection(Constants.KEY_COLLECTION_USERS).document(
            preferenceManager.getString(Constants.KEY_USER_ID)
        )
        documentReference.update(Constants.KEY_FCM_TOKEN, token)
            .addOnFailureListener{ _ -> showToast("Unable to update token")}

    }

    private fun signOut(){
        showToast("Signing out...")
        val database = FirebaseFirestore.getInstance()
        val documentReference = database.collection(Constants.KEY_COLLECTION_USERS).document(
            preferenceManager.getString(Constants.KEY_USER_ID)
        )
        val updates : HashMap<String, Any> = HashMap()
        updates[Constants.KEY_FCM_TOKEN] = FieldValue.delete()
        documentReference.update(updates)
            .addOnSuccessListener { _ ->
                preferenceManager.clear()
                startActivity(Intent(applicationContext, SignInActivity::class.java))
                finish()
            }
            .addOnFailureListener{ _ -> showToast("Unable to sing out")}
    }

    override fun onConversationClicked(user: User) {
        val intent = Intent(applicationContext, ChatActivity::class.java).apply {
            putExtra(Constants.KEY_USER, user)
        }
        startActivity(intent)
    }

}