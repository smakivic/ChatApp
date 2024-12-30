package com.example.myapplication.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.R
import com.example.myapplication.adapters.UsersAdapter
import com.example.myapplication.databinding.ActivityUsersBinding
import com.example.myapplication.listeners.UserListener
import com.example.myapplication.models.User
import com.example.myapplication.utilities.Constants
import com.example.myapplication.utilities.PreferenceManager
import com.google.firebase.firestore.FirebaseFirestore

class UsersActivity : AppCompatActivity(), UserListener {

    private lateinit var binding: ActivityUsersBinding
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_users)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferenceManager = PreferenceManager(applicationContext)
        setListeners()
        getUsers()
    }

    private fun setListeners() {
        binding.imageBack.setOnClickListener{
            finish()
        }
    }

    private fun getUsers(){
        loading(true)
        val database = FirebaseFirestore.getInstance()
        database.collection(Constants.KEY_COLLECTION_USERS)
            .get()
            .addOnSuccessListener { task ->

                loading(false)
                val currentUserId = preferenceManager.getString(Constants.KEY_USER_ID)
                if (task != null && !task.isEmpty) {
                    val users = ArrayList<User>()
                    for (queryDocumentSnapshot in task) {
                        if(currentUserId.equals(queryDocumentSnapshot.id)){
                            continue
                        }
                        val user = User()
                        user.name = queryDocumentSnapshot.getString(Constants.KEY_NAME)
                        user.email = queryDocumentSnapshot.getString(Constants.KEY_EMAIL)
                        user.image = queryDocumentSnapshot.getString(Constants.KEY_IMAGE)
                        user.token = queryDocumentSnapshot.getString(Constants.KEY_FCM_TOKEN)
                        user.id = queryDocumentSnapshot.getId()
                        users.add(user)
                    }
                    if(users.isNotEmpty()){
                        val usersAdapter = UsersAdapter(users, this)
                        binding.usersRecyclerView.adapter = usersAdapter
                        binding.usersRecyclerView.visibility = View.VISIBLE
                    }
                    else{
                        showErrorMessage()
                    }
                }
                else{
                    showErrorMessage()
                }

            }
    }

    private fun showErrorMessage(){
        binding.textErrorMessage.text = String.format("%s", "No users available")
    }

    private fun loading(isLoading : Boolean){
        if(isLoading){
            binding.progressBar.visibility = View.VISIBLE
        }else{
            binding.progressBar.visibility = View.INVISIBLE
        }
    }

    override fun onUserClicked(user: User?) {
        val intent = Intent(applicationContext, ChatActivity::class.java)
        intent.putExtra(Constants.KEY_USER, user)
        startActivity(intent)
        finish()
    }

}