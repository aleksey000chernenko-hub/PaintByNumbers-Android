package com.trudny.photoretouch

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PaintByNumbersApp() }
    }
}

@Composable
fun PaintByNumbersApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var acrylic by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<PbnResult?>(null) }
    var colorCount by remember { mutableFloatStateOf(24f) }
    var minRegion by remember { mutableFloatStateOf(45f) }
    var acrylicStrength by remember { mutableFloatStateOf(0.72f) }
    var processing by remember { mutableStateOf(false) }
    var view by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            processing = true
            val decoded = withContext(Dispatchers.IO) { decodeBitmap(context.contentResolver, uri, 2400) }
            original = decoded
            result = null
            view = 0
            acrylic = if (decoded != null) {
                withContext(Dispatchers.Default) { AcrylicPainter.render(decoded, 1600, acrylicStrength) }
            } else null
            processing = false
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Фото → акриловая картина", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("После выбора фото приложение сразу создаёт художественную версию в стиле акриловой живописи. Режим картины по номерам сохранён ниже.")

                Box(
                    Modifier.fillMaxWidth().height(430.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val shown = when (view) {
                        1 -> result?.numberedTemplate
                        2 -> result?.paletteSheet
                        else -> acrylic ?: result?.colorPreview ?: original
                    }
                    if (shown == null) {
                        Text("Выберите фотографию")
                    } else {
                        Image(
                            shown.asImageBitmap(),
                            "Предпросмотр",
                            Modifier.fillMaxSize().padding(6.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (processing) CircularProgressIndicator()
                }

                Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (original == null) "Выбрать фото" else "Выбрать другое фото")
                }

                if (original != null) {
                    Text("Сила акрилового эффекта: ${(acrylicStrength * 100).toInt()}%", fontWeight = FontWeight.Medium)
                    Slider(
                        value = acrylicStrength,
                        onValueChange = { acrylicStrength = it },
                        valueRange = 0.35f..1f
                    )
                    Button(
                        onClick = {
                            val src = original
                            if (src != null) scope.launch {
                                processing = true
                                acrylic = withContext(Dispatchers.Default) { AcrylicPainter.render(src, 1600, acrylicStrength) }
                                view = 0
                                processing = false
                            }
                        },
                        enabled = !processing,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Обновить акриловый эффект") }

                    acrylic?.let { art ->
                        Button(
                            onClick = {
                                scope.launch {
                                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    val ok = withContext(Dispatchers.IO) {
                                        savePng(context.contentResolver, art, "ACRYLIC_${stamp}.png", "Pictures/AcrylicPaintings")
                                    }
                                    Toast.makeText(context, if (ok) "Акриловая картина сохранена" else "Ошибка сохранения", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Сохранить акриловую картину") }
                    }

                    HorizontalDivider()
                    Text("Картина по номерам", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Количество красок: ${colorCount.toInt()}", fontWeight = FontWeight.Medium)
                    Slider(colorCount, { colorCount = it }, valueRange = 12f..36f, steps = 23)
                    Text("Упрощение мелких областей: ${minRegion.toInt()}", fontWeight = FontWeight.Medium)
                    Slider(minRegion, { minRegion = it }, valueRange = 15f..120f)

                    Button(
                        onClick = {
                            val src = acrylic ?: original
                            if (src != null) {
                                scope.launch {
                                    processing = true
                                    result = withContext(Dispatchers.Default) {
                                        PaintByNumbersGenerator.generate(src, colorCount.toInt(), 1200, minRegion.toInt())
                                    }
                                    view = 0
                                    processing = false
                                }
                            }
                        },
                        enabled = !processing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Создать картину по номерам")
                    }
                }

                result?.let { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(view == 0, { view = 0 }, { Text("Цвет") }, modifier = Modifier.weight(1f))
                        FilterChip(view == 1, { view = 1 }, { Text("Макет") }, modifier = Modifier.weight(1f))
                        FilterChip(view == 2, { view = 2 }, { Text("Палитра") }, modifier = Modifier.weight(1f))
                    }
                    Text("Сформировано ${r.palette.size} цветов. Каждый номер на макете соответствует номеру в палитре.")
                    Button(
                        onClick = {
                            scope.launch {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val ok = withContext(Dispatchers.IO) {
                                    savePng(context.contentResolver, r.colorPreview, "PBN_${stamp}_COLOR.png", "Pictures/PaintByNumbers") &&
                                        savePng(context.contentResolver, r.numberedTemplate, "PBN_${stamp}_TEMPLATE.png", "Pictures/PaintByNumbers") &&
                                        savePng(context.contentResolver, r.paletteSheet, "PBN_${stamp}_PALETTE.png", "Pictures/PaintByNumbers")
                                }
                                Toast.makeText(
                                    context,
                                    if (ok) "Комплект сохранён в Pictures/PaintByNumbers" else "Ошибка сохранения",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить весь комплект")
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun decodeBitmap(resolver: android.content.ContentResolver, uri: Uri, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

private fun savePng(
    resolver: android.content.ContentResolver,
    bitmap: Bitmap,
    name: String,
    relativePath: String
): Boolean {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        } ?: return false
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (_: Exception) {
        false
    }
}
