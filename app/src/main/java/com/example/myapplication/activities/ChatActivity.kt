package com.example.myapplication.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityChatBinding
import com.example.myapplication.models.User
import com.example.myapplication.utilities.Constants
import com.example.myapplication.utilities.PreferenceManager
import com.example.myapplication.adapters.ChatAdapter
import com.example.myapplication.adapters.RecentConversationsAdapter
import com.example.myapplication.models.ChatMessage
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : BaseActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var receiverUser: User


    private lateinit var chatMessages: MutableList<ChatMessage>
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var recentConversationsAdapter: RecentConversationsAdapter
    private lateinit var database: FirebaseFirestore
    private var conversationId: String? = null
    private var isReceiverAvailable: Boolean = false



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        this.binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(this.binding.root)
        loadReceiverDetails()
        setListeners()
        init()
        listenMessages()
//        preferenceManager = PreferenceManager(applicationContext)

    }

    private fun init() {
        preferenceManager = PreferenceManager(applicationContext)
        chatMessages = mutableListOf()
        chatAdapter = ChatAdapter(
            chatMessages,
            getBitmapFromEncodedString(receiverUser.image),
            preferenceManager.getString(Constants.KEY_USER_ID) ?: ""
        )
        binding.chatRecyclerView.adapter = chatAdapter
        database = FirebaseFirestore.getInstance()
    }

    private fun sendMessage() {
        val message = hashMapOf(
            Constants.KEY_SENDER_ID to preferenceManager.getString(Constants.KEY_USER_ID),
            Constants.KEY_RECEIVER_ID to receiverUser.id,
            Constants.KEY_MESSAGE to binding.inputMessage.text.toString(),
            Constants.KEY_TIMESTAMP to Date()
        )
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message)
        if (conversationId != null) {
            updateConversation(binding.inputMessage.text.toString())
        } else {
            val conversation = hashMapOf<String, Any>(
                Constants.KEY_SENDER_ID to preferenceManager.getString(Constants.KEY_USER_ID)!!,
                Constants.KEY_SENDER_NAME to preferenceManager.getString(Constants.KEY_NAME)!!,
                Constants.KEY_SENDER_IMAGE to preferenceManager.getString(Constants.KEY_IMAGE)!!,
                Constants.KEY_RECEIVER_ID to receiverUser.id,
                Constants.KEY_RECEIVER_NAME to receiverUser.name,
                Constants.KEY_RECEIVER_IMAGE to receiverUser.image,
                Constants.KEY_LAST_MESSAGE to binding.inputMessage.text.toString(),
                Constants.KEY_TIMESTAMP to Date()
            )
            addConversation(conversation)
        }

        binding.inputMessage.text = null
    }

    private fun listenAvailabilityOfReceiver() {
        database.collection(Constants.KEY_COLLECTION_USERS).document(receiverUser.id)
            .addSnapshotListener(this) { value, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (value != null) {
                    val availability = value.getLong(Constants.KEY_AVAILABILITY)?.toInt()
                    isReceiverAvailable = availability == 1
                }

                if (isReceiverAvailable) {
                    binding.textAvailability.visibility = View.VISIBLE
                } else {
                    binding.textAvailability.visibility = View.GONE
                }
            }
    }



    private fun listenMessages() {
        val currentUserId = preferenceManager.getString(Constants.KEY_USER_ID) ?: ""
        val receiverId = receiverUser.id

        database.collection(Constants.KEY_COLLECTION_CHAT)
            .whereEqualTo(Constants.KEY_SENDER_ID, currentUserId)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    Log.e("ChatActivity", "Error fetching sent messages: ${error.message}")
                    return@addSnapshotListener
                }
                Log.d("ChatActivity", "Sent messages retrieved: ${value?.documents?.size ?: 0}")
                processMessages(value)
            }

        database.collection(Constants.KEY_COLLECTION_CHAT)
            .whereEqualTo(Constants.KEY_SENDER_ID, receiverId)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, currentUserId)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    Log.e("ChatActivity", "Error fetching received messages: ${error.message}")
                    return@addSnapshotListener
                }
                Log.d("ChatActivity", "Received messages retrieved: ${value?.documents?.size ?: 0}")
                processMessages(value)
            }
    }

    private fun processMessages(value: QuerySnapshot?) {
        if (value != null) {
            val count = chatMessages.size
            for (documentChange in value.documentChanges) {
                if (documentChange.type == DocumentChange.Type.ADDED) {
                    val document = documentChange.document
                    val chatMessage = ChatMessage().apply {
                        senderId = document.getString(Constants.KEY_SENDER_ID)
                        receiverId = document.getString(Constants.KEY_RECEIVER_ID)
                        message = document.getString(Constants.KEY_MESSAGE)
                        dateTime = getReadableDateTime(document.getDate(Constants.KEY_TIMESTAMP)!!)
                        dateObject = document.getDate(Constants.KEY_TIMESTAMP)
                    }
                    chatMessages.add(chatMessage)
                    Log.d("ChatActivity", "Message added: $chatMessage")
                }
            }
            chatMessages.sortBy { it.dateObject }
            if (count == 0) {
                chatAdapter.notifyDataSetChanged()
            } else {
                chatAdapter.notifyItemRangeInserted(count, chatMessages.size - count)
                binding.chatRecyclerView.scrollToPosition(chatMessages.size - 1)
            }
            binding.chatRecyclerView.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE

            if (conversationId == null) {
                checkForConversation()
            }
        }
    }



    private val eventListener = EventListener<QuerySnapshot> { value, error ->
        if (error != null) {
            return@EventListener
        }
        if (value != null) {
            val count = chatMessages.size
            for (documentChange in value.documentChanges) {
                if (documentChange.type == DocumentChange.Type.ADDED) {
                    val chatMessage = ChatMessage().apply {
                        senderId = documentChange.document.getString(Constants.KEY_SENDER_ID)
                        receiverId = documentChange.document.getString(Constants.KEY_RECEIVER_ID)
                        message = documentChange.document.getString(Constants.KEY_MESSAGE)
                        dateTime = getReadableDateTime(documentChange.document.getDate(Constants.KEY_TIMESTAMP)!!)
                        dateObject = documentChange.document.getDate(Constants.KEY_TIMESTAMP)
                    }
                    chatMessages.add(chatMessage)
                }
            }
            chatMessages.sortBy { it.dateObject }
            if (count == 0) {
                chatAdapter.notifyDataSetChanged()
            } else {
                chatAdapter.notifyItemRangeInserted(count, chatMessages.size - count)
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
            }
            binding.chatRecyclerView.visibility = View.VISIBLE
        }
        binding.progressBar.visibility = View.GONE

        if (conversationId == null) {
            checkForConversation()
        }
    }



    private fun getBitmapFromEncodedString(encodedImage: String): Bitmap {
        val bytes = Base64.decode(encodedImage, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    @Suppress("DEPRECATION")
    private fun loadReceiverDetails(){
        receiverUser = intent.getSerializableExtra(Constants.KEY_USER) as User
        binding.textName.text = receiverUser.name
    }


    private fun setListeners() {
        binding.imageBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.layoutSend.setOnClickListener { sendMessage() }
        binding.imageInfo.setOnClickListener { deleteChat() }

    }

    private fun deleteChat() {
        val currentUserId = preferenceManager.getString(Constants.KEY_USER_ID) ?: ""
        val receiverId = receiverUser.id

        val chatRef = database.collection(Constants.KEY_COLLECTION_CHAT)
        chatRef
            .whereEqualTo(Constants.KEY_SENDER_ID, currentUserId)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
            .get()
            .addOnSuccessListener { snapshot ->
                for (document in snapshot.documents) {
                    document.reference.delete()
                }
            }
        chatRef
            .whereEqualTo(Constants.KEY_SENDER_ID, receiverId)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, currentUserId)
            .get()
            .addOnSuccessListener { snapshot ->
                for (document in snapshot.documents) {
                    document.reference.delete()
                }
            }

        if (conversationId != null) {
            Log.d("ChatActivity", "conversationId: $conversationId")
            database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .document(conversationId!!)
                .delete()
                .addOnSuccessListener {
                    Log.d("ChatActivity", "Conversation deleted successfully")
                    val intent = Intent(applicationContext, MainActivity::class.java).apply {
                        putExtra("REMOVE_CONVERSATION_ID", conversationId)
                    }
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e("ChatActivity", "Error deleting conversation: ${e.message}")
                }
        }
    }

    private fun notifyRecentConversationsUpdated() {
        recentConversationsAdapter?.removeConversation(conversationId!!)
    }


    private fun getReadableDateTime(date: Date): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(date)
    }


    private fun addConversation(conversation: HashMap<String, Any>) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .add(conversation)
            .addOnSuccessListener { documentReference ->
                conversationId = documentReference.id
            }
    }

    private fun updateConversation(message: String) {
        val documentReference = database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversationId!!)
        documentReference.update(
            Constants.KEY_LAST_MESSAGE, message,
            Constants.KEY_TIMESTAMP, Date()
        )
    }



    private fun checkForConversation() {
        if (chatMessages.isNotEmpty()) {
            checkForConversationRemotely(
                preferenceManager.getString(Constants.KEY_USER_ID) ?: "",
                receiverUser.id
            )
            checkForConversationRemotely(
                receiverUser.id,
                preferenceManager.getString(Constants.KEY_USER_ID) ?: ""
            )
        }
    }

    private fun checkForConversationRemotely(senderId: String, receiverId: String) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
            .get()
            .addOnCompleteListener(conversationOnCompleteListener)
    }

    private val conversationOnCompleteListener = OnCompleteListener<QuerySnapshot> { task ->
        if (task.isSuccessful && task.result != null && task.result!!.documents.isNotEmpty()) {
            val documentSnapshot = task.result!!.documents[0]
            conversationId = documentSnapshot.id
        }
    }

    override fun onResume() {
        super.onResume()
        listenAvailabilityOfReceiver()
    }


}