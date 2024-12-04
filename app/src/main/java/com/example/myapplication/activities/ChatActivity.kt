package com.example.myapplication.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityChatBinding
import com.example.myapplication.models.User
import com.example.myapplication.utilities.Constants
import com.example.myapplication.utilities.PreferenceManager

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
//    private lateinit var preferenceManager: PreferenceManager
    private lateinit var recieverUser: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadRecieverDetails()
        setListeners()
//        preferenceManager = PreferenceManager(applicationContext)

    }

    private fun loadRecieverDetails(){
        recieverUser = intent.getSerializableExtra(Constants.KEY_USER) as User
        binding.textName.text = recieverUser.name
    }

    private fun setListeners(){
        binding.imageBack.setOnClickListener{
            finish()
        }
    }
}