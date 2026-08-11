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
    var cropPreview by remember { mutableStateOf<Bitmap?>(null) }
    var cropped by remember { mutableStateOf<Bitmap?>(null) }
    var acrylic by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<PbnResult?>(null) }

    var selectedCanvas by remember { mutableStateOf(CanvasCropper.sizes.first()) }
    var cropOffsetX by remember { mutableFloatStateOf(0.5f) }
    var cropOffsetY by remember { mutableFloatStateOf(0.5f) }
    var cropConfirmed by remember { mutableStateOf(false) }

    var colorCount by remember { mutableFloatStateOf(24f) }
    var minRegion by remember { mutableFloatStateOf(45f) }
    var acrylicStrength by remember { mutableFloatStateOf(0.72f) }
    var processing by remember { mutableStateOf(false) }
    var view by remember { mutableIntStateOf(0) }

    fun refreshCropPreview() {
        val src = original ?: return
        cropPreview = CanvasCropper.crop(src, selectedCanvas, cropOffsetX, cropOffsetY)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            processing = true
            val decoded = withContext(Dispatchers.IO) { decodeBitmap(context.contentResolver, uri, 2400) }
            original = decoded
            selectedCanvas = CanvasCropper.sizes.first()
            cropOffsetX = 0.5f
            cropOffsetY = 0.5f
            cropConfirmed = false
            cropped = null
            acrylic = null
            result = null
            view = 0
            cropPreview = decoded?.let { CanvasCropper.crop(it, selectedCanvas, cropOffsetX, cropOffsetY) }
            processing = false
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Фото → акрил → холст → картина по номерам", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Сначала выберите фото, затем размер холста и кадрирование. После подтверждения приложение автоматически создаст акриловую версию.")

                Box(
                    Modifier.fillMaxWidth().height(430.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val shown = when {
                        !cropConfirmed -> cropPreview ?: original
                        view == 1 -> result?.numberedTemplate
                        view == 2 -> result?.paletteSheet
                        else -> acrylic ?: result?.colorPreview ?: cropped ?: original
                    }
                    if (shown == null) {
                        Text("Выберите фотографию")
                    } else {
                        Image(shown.asImageBitmap(), "Предпросмотр", Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                    }
                    if (processing) CircularProgressIndicator()
                }

                Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (original == null) "Выбрать фото" else "Выбрать другое фото")
                }

                if (original != null && !cropConfirmed) {
                    HorizontalDivider()
                    Text("1. Выберите размер холста", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    CanvasCropper.sizes.forEach { size ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedCanvas == size,
                                onClick = {
                                    selectedCanvas = size
                                    refreshCropPreview()
                                }
                            )
                            Text(size.label)
                        }
                    }

                    Text("2. Настройте кадрирование", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Горизонтальное смещение")
                    Slider(
                        value = cropOffsetX,
                        onValueChange = {
                            cropOffsetX = it
                            refreshCropPreview()
                        },
                        valueRange = 0f..1f
                    )
                    Text("Вертикальное смещение")
                    Slider(
                        value = cropOffsetY,
                        onValueChange = {
                            cropOffsetY = it
                            refreshCropPreview()
                        },
                        valueRange = 0f..1f
                    )
                    Text("Предпросмотр выше уже показан в пропорциях ${selectedCanvas.label}.", style = MaterialTheme.typography.bodySmall)

                    Button(
                        onClick = {
                            val src = original
                            if (src != null) scope.launch {
                                processing = true
                                val finalCrop = withContext(Dispatchers.Default) {
                                    CanvasCropper.crop(src, selectedCanvas, cropOffsetX, cropOffsetY)
                                }
                                cropped = finalCrop
                                acrylic = withContext(Dispatchers.Default) {
                                    AcrylicPainter.render(finalCrop, 1600, acrylicStrength)
                                }
                                cropConfirmed = true
                                result = null
                                view = 0
                                processing = false
                            }
                        },
                        enabled = !processing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Подтвердить кадрирование и создать акрил")
                    }
                }

                if (cropConfirmed && cropped != null) {
                    Text("Холст: ${selectedCanvas.label}", fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = {
                            cropConfirmed = false
                            result = null
                            view = 0
                            refreshCropPreview()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Изменить размер или кадрирование") }

                    Text("Сила акрилового эффекта: ${(acrylicStrength * 100).toInt()}%", fontWeight = FontWeight.Medium)
                    Slider(value = acrylicStrength, onValueChange = { acrylicStrength = it }, valueRange = 0.35f..1f)
                    Button(
                        onClick = {
                            val src = cropped
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
                                    val name = "ACRYLIC_${selectedCanvas.widthCm}x${selectedCanvas.heightCm}_${stamp}.png"
                                    val ok = withContext(Dispatchers.IO) {
                                        savePng(context.contentResolver, art, name, "Pictures/AcrylicPaintings")
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
                            val src = acrylic ?: cropped
                            if (src != null) scope.launch {
                                processing = true
                                result = withContext(Dispatchers.Default) {
                                    PaintByNumbersGenerator.generate(src, colorCount.toInt(), 1200, minRegion.toInt())
                                }
                                view = 0
                                processing = false
                            }
                        },
                        enabled = !processing,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Создать картину по номерам") }
                }

                result?.let { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(view == 0, { view = 0 }, { Text("Цвет") }, modifier = Modifier.weight(1f))
                        FilterChip(view == 1, { view = 1 }, { Text("Макет") }, modifier = Modifier.weight(1f))
                        FilterChip(view == 2, { view = 2 }, { Text("Палитра") }, modifier = Modifier.weight(1f))
                    }
                    Text("Сформировано ${r.palette.size} цветов. Макет рассчитан под холст ${selectedCanvas.label}.")
                    Button(
                        onClick = {
                            scope.launch {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val prefix = "PBN_${selectedCanvas.widthCm}x${selectedCanvas.heightCm}_${stamp}"
                                val ok = withContext(Dispatchers.IO) {
                                    savePng(context.contentResolver, r.colorPreview, "${prefix}_COLOR.png", "Pictures/PaintByNumbers") &&
                                        savePng(context.contentResolver, r.numberedTemplate, "${prefix}_TEMPLATE.png", "Pictures/PaintByNumbers") &&
                                        savePng(context.contentResolver, r.paletteSheet, "${prefix}_PALETTE.png", "Pictures/PaintByNumbers")
                                }
                                Toast.makeText(context, if (ok) "Комплект сохранён" else "Ошибка сохранения", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Сохранить весь комплект") }
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
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (_: Exception) {
        false
    }
}
