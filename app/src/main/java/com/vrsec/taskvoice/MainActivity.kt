package com.vrsec.taskvoice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var shoppingListAdapter: ShoppingListAdapter
    private val shoppingList = mutableListOf<String>()
    private lateinit var tts: TextToSpeech
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Database
        database = FirebaseDatabase.getInstance().getReference("shopping_list")

        // Fetch existing items from Firebase
        fetchShoppingListFromFirebase()

        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        shoppingListAdapter = ShoppingListAdapter(shoppingList, this::deleteItem)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = shoppingListAdapter

        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            }
        }

        findViewById<Button>(R.id.btn_voice_input).setOnClickListener {
            startVoiceRecognition()
        }

        findViewById<Button>(R.id.btn_clear_list).setOnClickListener {
            clearShoppingList()
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        voiceRecognitionLauncher.launch(intent)
    }

    private val voiceRecognitionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.let { results ->
                results.firstOrNull()?.let { recognizedText ->
                    addToShoppingList(recognizedText)
                }
            }
        }
    }

    private fun addToShoppingList(item: String) {
        shoppingList.add(item)
        shoppingListAdapter.notifyItemInserted(shoppingList.size - 1)
        speak(item)
        saveToFirebase(item) // Save to Firebase
    }

    private fun saveToFirebase(item: String) {
        val itemId = database.push().key // Generate a unique key
        itemId?.let {
            database.child(it).setValue(item)
                .addOnSuccessListener {
                    Toast.makeText(this, "Item added to Firebase", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to add item to Firebase", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun fetchShoppingListFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                shoppingList.clear() // Clear existing items
                for (itemSnapshot in snapshot.children) {
                    val item = itemSnapshot.getValue(String::class.java)
                    item?.let {
                        shoppingList.add(it) // Add items to the local list
                    }
                }
                Log.d("FirebaseData", "Fetched items: $shoppingList") // Log fetched items
                shoppingListAdapter.notifyDataSetChanged() // Notify adapter of data change
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Failed to fetch data", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun deleteItem(position: Int) {
        val item = shoppingList[position]
        shoppingList.removeAt(position)
        shoppingListAdapter.notifyItemRemoved(position)
        deleteFromFirebase(item)
    }

    private fun deleteFromFirebase(item: String) {
        database.orderByValue().equalTo(item).get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                child.ref.removeValue()
            }
            Toast.makeText(this, "Item deleted from Firebase", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to delete item from Firebase", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearShoppingList() {
        shoppingList.clear()
        shoppingListAdapter.notifyDataSetChanged()
        clearFirebaseList()
        Toast.makeText(this, "Shopping list cleared", Toast.LENGTH_SHORT).show()
    }

    private fun clearFirebaseList() {
        database.removeValue().addOnSuccessListener {
            Toast.makeText(this, "Firebase shopping list cleared", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to clear Firebase shopping list", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speak(text: String) {
        tts.speak("Added $text to your shopping list", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
    }
}
