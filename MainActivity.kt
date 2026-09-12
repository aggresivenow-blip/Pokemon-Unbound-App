package com.example.unbounddex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

private data class Pokemon(
    val number: Int,
    val name: String,
    val types: List<String>,
    val abilities: List<String>,
    val hiddenAbility: String,
    val stats: Map<String, Int>,
    val locations: List<String>,
    val evolutions: List<String>,
    val sprite: String
)

private data class Encounter(
    val pokemon: String,
    val method: String,
    val rate: Int? = null
)

private data class Location(
    val name: String,
    val methods: List<Pair<String, List<Encounter>>>,
    val exits: List<String>,
    val points: List<String>,
    val mapUrl: String
)

private data class MapNode(
    val id: String,
    val name: String,
    val region: String,
    val x: Float,
    val y: Float
)

private object DataStore {
    fun load(context: android.content.Context): Pair<List<Pokemon>, List<Location>> {
        val raw = runCatching {
            context.assets.open("data/unbound-data.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return fallback()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return fallback()

        val pokemonArray = root.optJSONArray("pokemon") ?: JSONArray()
        val locationArray = root.optJSONArray("locations") ?: JSONArray()

        val pokemon = buildList {
            for (i in 0 until pokemonArray.length()) {
                val o = pokemonArray.optJSONObject(i) ?: continue
                val name = o.optString("name").takeIf { it.isNotBlank() } ?: continue
                val number = o.optInt("guideNumber", i + 1)
                add(
                    Pokemon(
                        number = number,
                        name = name,
                        types = stringArray(o, "types"),
                        abilities = stringArray(o, "abilities"),
                        hiddenAbility = o.optString("hiddenAbility"),
                        stats = intMap(o.optJSONObject("stats")),
                        locations = locationNames(o.optJSONArray("locations")),
                        evolutions = evolutionNames(o.optJSONArray("evolutions")),
                        sprite = o.optString("sprite")
                    )
                )
            }
        }.sortedBy { it.number }

        val locations = buildList {
            for (i in 0 until locationArray.length()) {
                val o = locationArray.optJSONObject(i) ?: continue
                val name = o.optString("name").takeIf { it.isNotBlank() } ?: continue
                val methods = mutableListOf<Pair<String, List<Encounter>>>()
                val methodArray = o.optJSONArray("methods")
                if (methodArray != null) {
                    for (j in 0 until methodArray.length()) {
                        val m = methodArray.optJSONObject(j) ?: continue
                        val label = m.optString("label", "Encounter")
                        val rows = mutableListOf<Encounter>()
                        val encounters = m.optJSONArray("encounters")
                        if (encounters != null) {
                            for (k in 0 until encounters.length()) {
                                val e = encounters.optJSONObject(k) ?: continue
                                val species = e.optString("species").ifBlank { continue }
                                val rate = if (e.has("rate") && !e.isNull("rate")) e.optInt("rate") else null
                                rows += Encounter(species, label, rate)
                            }
                        } else {
                            val species = m.optJSONArray("species")
                            if (species != null) for (k in 0 until species.length()) {
                                species.optString(k).takeIf { it.isNotBlank() }?.let { rows += Encounter(it, label) }
                            }
                        }
                        if (rows.isNotEmpty()) methods += label to rows
                    }
                }
                add(
                    Location(
                        name = name,
                        methods = methods,
                        exits = stringArray(o, "exits"),
                        points = stringArray(o, "pointsOfInterest"),
                        mapUrl = o.optString("mapUrl")
                    )
                )
            }
        }.distinctBy { it.name }

        return if (pokemon.isNotEmpty()) pokemon to locations else fallback()
    }

    private fun stringArray(o: JSONObject, key: String): List<String> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return buildList { for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let(::add) }
    }

    private fun locationNames(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i)
                if (o != null) o.optString("location").takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun evolutionNames(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val name = o.optString("targetName").ifBlank { o.optString("target") }
                if (name.isNotBlank()) add(name)
            }
        }
    }

    private fun intMap(o: JSONObject?): Map<String, Int> {
        if (o == null) return emptyMap()
        return buildMap { val keys = o.keys(); while (keys.hasNext()) { val k = keys.next(); put(k, o.optInt(k)) } }
    }

    private fun fallback(): Pair<List<Pokemon>, List<Location>> =
        listOf(Pokemon(1, "Data unavailable", emptyList(), emptyList(), "", emptyMap(), emptyList(), emptyList(), "")) to
            listOf(Location("Data unavailable", emptyList(), emptyList(), emptyList(), ""))
}

private val mapNodes = listOf(
    MapNode("route-1", "Route 1", "West", .11f, .15f), MapNode("route-2", "Route 2", "West", .20f, .21f),
    MapNode("route-3", "Route 3", "West", .29f, .27f), MapNode("route-4", "Route 4", "West", .38f, .32f),
    MapNode("route-5", "Route 5", "West", .47f, .38f), MapNode("route-6", "Route 6", "Central", .56f, .36f),
    MapNode("route-7", "Route 7", "Central", .65f, .32f), MapNode("route-8", "Route 8", "Central", .74f, .38f),
    MapNode("route-9", "Route 9", "Central", .68f, .50f), MapNode("route-10", "Route 10", "Central", .58f, .55f),
    MapNode("route-11", "Route 11", "Central", .48f, .61f), MapNode("route-12", "Route 12", "East", .39f, .67f),
    MapNode("route-13", "Route 13", "East", .48f, .75f), MapNode("route-14", "Route 14", "East", .59f, .78f),
    MapNode("route-15", "Route 15", "East", .70f, .73f), MapNode("route-16", "Route 16", "East", .80f, .65f),
    MapNode("route-17", "Route 17", "East", .81f, .53f), MapNode("route-18", "Route 18", "East", .81f, .42f),
    MapNode("icicle-cave", "Icicle Cave", "West", .15f, .27f), MapNode("grim-woods", "Grim Woods", "West", .34f, .39f),
    MapNode("cinder-volcano", "Cinder Volcano", "Central", .51f, .25f), MapNode("valley-cave", "Valley Cave", "Central", .62f, .29f),
    MapNode("thundercap", "Thundercap Mountain", "East", .72f, .22f), MapNode("great-desert", "Great Desert", "East", .36f, .50f),
    MapNode("ruins", "Ruins of Void", "East", .61f, .61f), MapNode("tomb", "Tomb of Borrius", "Post-game", .42f, .84f),
    MapNode("safari", "Safari Zone", "East", .69f, .85f), MapNode("flower", "Flower Paradise", "Post-game", .16f, .82f),
    MapNode("victory", "Victory Road", "League", .88f, .21f)
)

private val mapEdges = listOf(
    "route-1" to "route-2", "route-2" to "route-3", "route-3" to "route-4", "route-4" to "route-5",
    "route-5" to "route-6", "route-6" to "route-7", "route-7" to "route-8", "route-8" to "route-9",
    "route-9" to "route-10", "route-10" to "route-11", "route-11" to "route-12", "route-12" to "route-13",
    "route-13" to "route-14", "route-14" to "route-15", "route-15" to "route-16", "route-16" to "route-17",
    "route-17" to "route-18", "route-1" to "icicle-cave", "route-4" to "grim-woods", "route-6" to "cinder-volcano",
    "route-7" to "valley-cave", "route-13" to "thundercap", "route-11" to "great-desert", "route-14" to "ruins",
    "route-18" to "victory", "great-desert" to "tomb", "route-16" to "safari", "route-3" to "flower"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BorriusMap(onSelect: (MapNode) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val nodes = remember { mapNodes.associateBy { it.id } }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(510.dp).clip(MaterialTheme.shapes.large)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.85f, 3.5f)
                    offset += pan
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            transformOrigin = TransformOrigin(0.5f, 0.5f)
        }) {
            val w = size.width; val h = size.height
            drawRect()
            mapEdges.forEach { (a, b) ->
                val p = nodes[a] ?: return@forEach; val q = nodes[b] ?: return@forEach
                drawLine(Offset(w * p.x, h * p.y), Offset(w * q.x, h * q.y), strokeWidth = 6f)
            }
            nodes.values.forEach { n ->
                drawCircle(radius = 15f, center = Offset(w * n.x, h * n.y))
                drawContext.canvas.nativeCanvas.drawText(
                    n.name, w * n.x + 20f, h * n.y + 6f,
                    android.graphics.Paint().apply { isAntiAlias = true; textSize = 26f }
                )
            }
        }
    }
}

@Composable
private fun DexScreen(pokemon: List<Pokemon>, onSelect: (Pokemon) -> Unit) {
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("All") }
    val types = listOf("All", "Normal", "Fire", "Water", "Electric", "Grass", "Ice", "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug", "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy")
    val filtered = remember(pokemon, query, type) {
        pokemon.filter { p ->
            (query.isBlank() || p.name.contains(query, true) || p.number.toString() == query.trim()) &&
                (type == "All" || p.types.any { it.equals(type, true) })
        }
    }
    OutlinedTextField(query, { query = it }, label = { Text("Search Pokémon or number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Spacer(Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) { items(types) { t -> FilterChip(type == t, { type = t }, label = { Text(t) }) } }
    Spacer(Modifier.height(6.dp))
    Text("${filtered.size} entries", style = MaterialTheme.typography.labelMedium)
    LazyColumn(Modifier.fillMaxSize()) {
        items(filtered) { p ->
            ListItem(
                headlineContent = { Text("#${p.number.toString().padStart(3, '0')}  ${p.name}") },
                supportingContent = { Text(p.types.joinToString(" / ").ifBlank { "Type unknown" }) },
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun PokemonDetail(p: Pokemon, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(p.name, style = MaterialTheme.typography.headlineSmall)
        Text("#${p.number.toString().padStart(3, '0')} • ${p.types.joinToString(" / ")}")
        Spacer(Modifier.height(10.dp))
        if (p.stats.isNotEmpty()) Text("Base stats: " + p.stats.entries.joinToString(" • ") { "${it.key}: ${it.value}" })
        if (p.abilities.isNotEmpty()) Text("Abilities: ${p.abilities.joinToString(", ")}")
        if (p.hiddenAbility.isNotBlank()) Text("Hidden Ability: ${p.hiddenAbility}")
        if (p.evolutions.isNotEmpty()) Text("Evolutions: ${p.evolutions.joinToString(", ")}")
        Spacer(Modifier.height(10.dp))
        Text("Known locations", style = MaterialTheme.typography.titleMedium)
        p.locations.forEach { Text("• $it") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun MapScreen(locations: List<Location>, onSelectPokemon: (String) -> Unit) {
    var selected by remember { mutableStateOf<Location?>(null) }
    if (selected == null) {
        Text("Borrius map", style = MaterialTheme.typography.titleLarge)
        Text("Pinch/drag the map. Tap a route or area for its encounter list.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        BorriusMap { node -> selected = locations.firstOrNull { it.name.equals(node.name, true) } ?: Location(node.name, emptyList(), emptyList(), emptyList(), "") }
    } else {
        val location = selected!!
        Text(location.name, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        if (location.methods.isEmpty()) Text("No wild encounter rows were supplied for this location in the source dataset.")
        LazyColumn(Modifier.weight(1f)) {
            location.methods.forEach { (method, rows) ->
                item { Text(method, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(3.dp)) }
                items(rows) { e ->
                    ListItem(headlineContent = { Text(e.pokemon) }, supportingContent = { Text(e.rate?.let { "$it%" } ?: "Encounter recorded") })
                    HorizontalDivider()
                }
            }
        }
        if (location.exits.isNotEmpty()) Text("Exits: ${location.exits.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
        OutlinedButton({ selected = null }, Modifier.fillMaxWidth()) { Text("Back to map") }
    }
}

@Composable
fun UnboundDexApp() {
    val context = LocalContext.current
    val data = remember { DataStore.load(context) }
    var tab by remember { mutableIntStateOf(0) }
    var selectedPokemon by remember { mutableStateOf<Pokemon?>(null) }
    val pokemon = data.first
    val locations = data.second
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(tab == 0, { tab = 0; selectedPokemon = null }, label = { Text("Dex") }, icon = { Text("◉") })
            NavigationBarItem(tab == 1, { tab = 1; selectedPokemon = null }, label = { Text("Map") }, icon = { Text("⌖") })
        }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Unbound Dex", style = MaterialTheme.typography.headlineMedium)
            Text("Offline • source-backed • Pokémon Unbound 2.1.1.1 data pipeline", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            if (selectedPokemon != null) PokemonDetail(selectedPokemon!!) { selectedPokemon = null }
            else if (tab == 0) DexScreen(pokemon) { selectedPokemon = it }
            else MapScreen(locations) { }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { UnboundDexApp() } }
    }
}
