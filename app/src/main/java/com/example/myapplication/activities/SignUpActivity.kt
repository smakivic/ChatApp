package com.example.myapplication.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivitySignUpBinding
import com.example.myapplication.utilities.Constants
import com.example.myapplication.utilities.PreferenceManager
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var preferenceManager: PreferenceManager
    private var encodedImage: String = "";


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(applicationContext)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setListeners()
    }

    private fun setListeners() {
        binding.textSignIn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.textSignIn.setOnClickListener {
            startActivity(Intent(applicationContext, SignInActivity::class.java))
        }
        binding.buttonSignUp.setOnClickListener{
            if(isValidSignUpDetails()){
                signUp()
            }
        }
        binding.layoutImage.setOnClickListener{
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            pickImage.launch(intent)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun signUp(){

        loading(true)
        val database : FirebaseFirestore = FirebaseFirestore.getInstance()
        val user : HashMap<String, Any> = HashMap()
        user[Constants.KEY_NAME] = binding.inputName.text.toString()
        user[Constants.KEY_EMAIL] = binding.inputMail.text.toString()
        user[Constants.KEY_PASSWORD] = binding.inputPassword.text.toString()
        user[Constants.KEY_IMAGE] = encodedImage
        database.collection(Constants.KEY_COLLECTION_USERS)
            .add(user)
            .addOnSuccessListener { documentReference ->
                loading(false)
                preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true)
                preferenceManager.putString(Constants.KEY_USER_ID,documentReference.id)
                preferenceManager.putString(Constants.KEY_NAME, binding.inputName.text.toString())
                preferenceManager.putString(Constants.KEY_IMAGE, encodedImage)
                val intent = Intent(applicationContext, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            }
            .addOnFailureListener { exception ->
                loading(false)
                showToast(exception.message.toString())
            }

    }
    private fun encodeImage(bitmap : Bitmap): String{
        val previewWidth = 150
        val previewHeight: Int = bitmap.height * previewWidth/bitmap.width
        val previewBitmap : Bitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, false)
        val byteArrayOutputStream = ByteArrayOutputStream()
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
        val bytes : ByteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                val imageUri: Uri? = data.data
                try {
                    val inputStream = contentResolver.openInputStream(imageUri!!)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    binding.imageProfile.setImageBitmap(bitmap)
                    binding.textAddImage.visibility = View.GONE
                    encodedImage = encodeImage(bitmap)
                } catch (e: FileNotFoundException) {
                    Log.d("Sign up error", e.message.toString())
                    e.printStackTrace()
                }
            }
        }
    }


    private fun isValidSignUpDetails(): Boolean {
        if(encodedImage == ""){
            showToast("Select profile image")
            return false
        }else if(binding.inputName.text.toString().trim().isEmpty()){
            showToast("Enter name")
            return false
        }else if(binding.inputMail.text.toString().trim().isEmpty()){
            showToast("Enter email")
            return false
        }else if (!Patterns.EMAIL_ADDRESS.matcher(binding.inputMail.text.toString()).matches()) {
            showToast("Enter valid email")
            return false
        } else if(binding.inputPassword.text.toString().trim().isEmpty()){
            showToast("Enter password")
            return false
        } else if(binding.inputConfirmPassword.text.toString().trim().isEmpty()) {
            showToast("Confirm your password")
            return false
        } else if(!binding.inputPassword.text.toString().equals(binding.inputConfirmPassword.text.toString())){
            showToast("Password and confirm password don't match")
            return false
        }
        return true
    }

    private fun loading(isLoading : Boolean){
        if(isLoading){
            binding.buttonSignUp.visibility = View.INVISIBLE
            binding.progressBar.visibility = View.VISIBLE
        }else{
            binding.buttonSignUp.visibility = View.VISIBLE
            binding.progressBar.visibility = View.INVISIBLE
        }
    }


}