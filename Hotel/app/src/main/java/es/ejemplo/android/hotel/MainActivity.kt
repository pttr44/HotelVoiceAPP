package es.ejemplo.android.hotel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.ejemplo.android.hotel.ui.theme.HotelTheme
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

// Modelo de datos para las habitaciones del XML
data class Habitacion(
    val numero: String,
    val estado: String,
    val planta: String,
    val tipo: String,
    val capacidad: Int,
    val precioNoche: Double,
    val vista: String,
    val accesible: String
)

// Modelo para cada mensaje en la conversación del chat
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val habitacionCard: Habitacion? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Cargar habitaciones desde reservas.xml en assets
        val habitaciones = cargarHabitacionesDesdeXML()

        setContent {
            HotelTheme {
                HotelChatScreen(habitaciones = habitaciones)
            }
        }
    }

    private fun cargarHabitacionesDesdeXML(): List<Habitacion> {
        val lista = mutableListOf<Habitacion>()
        try {
            val inputStream = assets.open("reservas.xml")
            val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = docBuilder.parse(inputStream)
            val nodes = doc.getElementsByTagName("habitacion")

            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val hab = Habitacion(
                        numero = element.getAttribute("numero"),
                        estado = element.getAttribute("estado"),
                        planta = element.getAttribute("planta"),
                        tipo = element.getElementsByTagName("tipo").item(0).textContent,
                        capacidad = element.getElementsByTagName("capacidad").item(0).textContent.toInt(),
                        precioNoche = element.getElementsByTagName("precioNoche").item(0).textContent.toDouble(),
                        vista = element.getElementsByTagName("vista").item(0).textContent,
                        accesible = element.getElementsByTagName("accesible").item(0).textContent
                    )
                    lista.add(hab)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return lista
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotelChatScreen(habitaciones: List<Habitacion>) {
    var inputText by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            ChatMessage("¿En qué puedo ayudarte?", isUser = false)
        )
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0) ?: ""
            if (spokenText.isNotEmpty()) {
                procesarConsultaUsuario(spokenText, habitaciones, messages)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HotelApp - Asistente Virtual") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E88E5),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF4F6F8))
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg)
                }
            }

            val context = LocalContext.current
            Surface(
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe o habla...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    IconButton(onClick = {
                        if (inputText.isNotBlank()) {
                            val textToSend = inputText
                            inputText = ""
                            procesarConsultaUsuario(textToSend, habitaciones, messages)
                        }
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Color(0xFF1E88E5))
                    }

                    FloatingActionButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Dime qué tipo de habitación buscas...")
                            }
                            speechLauncher.launch(intent)
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color(0xFF1E88E5),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voz")
                    }
                }
            }
        }
    }
}

fun procesarConsultaUsuario(
    query: String,
    habitaciones: List<Habitacion>,
    messages: MutableList<ChatMessage>
) {
    messages.add(ChatMessage(query, isUser = true))

    val queryLower = query.lowercase()
    val disponibles = habitaciones.filter { it.estado.equals("disponible", ignoreCase = true) }

    when {
        queryLower.contains("doble") -> {
            val dobles = disponibles.filter { it.tipo.equals("Doble", ignoreCase = true) }
            val primera = dobles.firstOrNull()
            messages.add(
                ChatMessage(
                    text = "He encontrado ${dobles.size} habitaciones dobles disponibles. ¿Quieres verlas?",
                    isUser = false,
                    habitacionCard = primera
                )
            )
        }
        queryLower.contains("suite") -> {
            val suites = disponibles.filter { it.tipo.equals("Suite", ignoreCase = true) }
            val primera = suites.firstOrNull()
            messages.add(
                ChatMessage(
                    text = "Tenemos ${suites.size} suites disponibles actualmente.",
                    isUser = false,
                    habitacionCard = primera
                )
            )
        }
        queryLower.contains("individual") -> {
            val indiv = disponibles.filter { it.tipo.equals("Individual", ignoreCase = true) }
            messages.add(
                ChatMessage(
                    text = "Hay ${indiv.size} habitaciones individuales disponibles.",
                    isUser = false,
                    habitacionCard = indiv.firstOrNull()
                )
            )
        }
        else -> {
            messages.add(
                ChatMessage(
                    text = "Lo siento, ¿podrías especificar si buscas una habitación doble, individual o suite?",
                    isUser = false
                )
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bgColor = if (message.isUser) Color(0xFF1E88E5) else Color(0xFFE0E0E0)
    val textColor = if (message.isUser) Color.White else Color.Black

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bgColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                fontSize = 15.sp,
                modifier = Modifier.padding(12.dp)
            )
        }

        message.habitacionCard?.let { hab ->
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier
                    .width(260.dp)
                    .padding(start = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Habitación ${hab.numero}", style = MaterialTheme.typography.titleMedium)
                    Text(text = "${hab.tipo} - ${hab.capacidad} personas", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Text(text = "${hab.precioNoche} € / noche", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { /* Acción de reserva */ },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                    ) {
                        Text("Reservar")
                    }
                }
            }
        }
    }
}