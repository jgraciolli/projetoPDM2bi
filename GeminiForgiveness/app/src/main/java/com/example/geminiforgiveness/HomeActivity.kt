package com.example.geminiforgiveness

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class HomeActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private val apiKey = "AIzaSyBE5dKTb-oQe_biebzitjajzoCSwQNymL8"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        importarPedido()

        val euFizIsso = findViewById<EditText>(R.id.edtSituation)
        val tomPedido = findViewById<EditText>(R.id.edtTone)
        val btnGerar = findViewById<Button>(R.id.btnGenerate)
        val pedidoDesculpas = findViewById<TextView>(R.id.tvApology)

        btnGerar.setOnClickListener {
            if (euFizIsso.text.trim().isNotEmpty() && tomPedido.text.trim().isNotEmpty()){
                enviarPerguntaGemini (
                    "Gemini, por favor, preciso da sua ajuda! Eu estou em um relacionamento" +
                            "amoroso sério, e vacilei com meu parceiro, " + euFizIsso.text + ", preciso" +
                            "que você crie um pedido de desculpas dramático para eu usar, mas precisa ser" +
                            "em um tom " + tomPedido.text + "."
                ) { resposta ->
                    runOnUiThread {
                        pedidoDesculpas.text = resposta
                    }
                }
            } else {
                euFizIsso.text.trim()
                tomPedido.text.trim()

                Toast.makeText(this, "Preencha os campos faltantes para gerar seu pedido!",
                    Toast.LENGTH_SHORT).show()
            }
        }

        val btnSalvar = findViewById <TextView>(R.id.btnSave)
        btnSalvar.setOnClickListener {
            if (pedidoDesculpas.text.trim() != "") {
                val p = pedidoDesculpas.text.toString()
                val m = euFizIsso.text.toString()
                val tP = tomPedido.text.toString()

                salvarPedido(p, m, tP)
            } else {
                Toast.makeText(this, "Primeiro gere um pedido para salvar!", Toast.LENGTH_SHORT).show()
            }
        }

        val btnSair = findViewById <TextView>(R.id.btnLogout)
        btnSair.setOnClickListener {
            Firebase.auth.signOut()

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)

            finish()
        }
    }

    private fun enviarPerguntaGemini(pergunta: String, callback: (String) -> Unit) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
        val json = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", pergunta)
                ))
            ))
        }

        val mediaType = "application/json".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback("Falha na requisição: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val resposta = response.body?.string() ?: "Sem resposta"
                try {
                    val jsonResposta = JSONObject(resposta)
                    val texto = jsonResposta
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    callback(texto)
                } catch (e: Exception) {
                    callback("Erro ao ler resposta: ${e.message}")
                }
            }
        })
    }

    private fun salvarPedido(texto: String, motivo: String, tom: String) {
        val currentUser = Firebase.auth.currentUser

        val userApologiesRef = currentUser?.let {
            Firebase.database.reference
                .child("users")
                .child(it.uid)
                .child("apology")
        }

        val apologyData = hashMapOf(
            "text" to texto,
            "reason" to motivo,
            "tone" to tom
        )

        if (userApologiesRef != null) {
            userApologiesRef.setValue(apologyData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Pedido salvo com sucesso!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Erro ao salvar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    data class Pedido(
        val text: String = "",
        val reason: String = "",
        val tone: String = "",
    )

    private fun importarPedido() {
        val currentUser = Firebase.auth.currentUser ?: return

        val euFizIsso = findViewById<EditText>(R.id.edtSituation)
        val tom = findViewById<EditText>(R.id.edtTone)
        val pedidoDesculpas = findViewById<TextView>(R.id.tvApology)

        Firebase.database.reference
            .child("users")
            .child(currentUser.uid)
            .child("apology")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val register = snapshot.getValue(Pedido::class.java)?.apply {
                            euFizIsso.setText(this.reason)
                            tom.setText(this.tone)
                            pedidoDesculpas.text = this.text
                        }
                        Toast.makeText(
                            this@HomeActivity,
                            "Pedido anterior carregado!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("LOAD_APOLOGY", "Erro ao carregar: ${error.message}")
                }
            })
    }
}