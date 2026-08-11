package com.trudny.photoretouch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

data class PbnColor(val number: Int, val color: Int, val pixelCount: Int)
data class PbnResult(val colorPreview: Bitmap, val numberedTemplate: Bitmap, val paletteSheet: Bitmap, val palette: List<PbnColor>)

object PaintByNumbersGenerator {
    fun generate(source: Bitmap, colorCount: Int = 24, maxSide: Int = 1200, minRegionPixels: Int = 45): PbnResult {
        val scaled = scaleDown(source, maxSide); val w = scaled.width; val h = scaled.height
        val pixels = IntArray(w * h); scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        val k = colorCount.coerceIn(6, 36); val centers = kMeansCenters(pixels, k)
        var labels = assignLabels(pixels, centers); labels = mergeSmallRegions(labels, w, h, minRegionPixels.coerceAtLeast(8), centers)
        val counts = IntArray(k); labels.forEach { if (it in counts.indices) counts[it]++ }
        val order = counts.indices.sortedByDescending { counts[it] }; val remap = IntArray(k)
        order.forEachIndexed { idx, old -> remap[old] = idx }; val orderedCenters = order.map { centers[it] }
        labels = IntArray(labels.size) { remap[labels[it]] }
        val previewPixels = IntArray(pixels.size) { orderedCenters[labels[it]] }
        val preview = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(previewPixels, 0, w, 0, 0, w, h) }
        val template = drawTemplate(labels, w, h)
        val palette = orderedCenters.mapIndexed { index, color -> PbnColor(index + 1, color, labels.count { it == index }) }
        return PbnResult(preview, template, drawPalette(palette), palette)
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val largest = max(src.width, src.height); if (largest <= maxSide) return src.copy(Bitmap.Config.ARGB_8888, false)
        val ratio = maxSide.toFloat() / largest; return Bitmap.createScaledBitmap(src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true)
    }

    private fun kMeansCenters(pixels: IntArray, k: Int): IntArray {
        val sampleStep = max(1, pixels.size / 30000); val samples = ArrayList<Int>(); var i = 0
        while (i < pixels.size) { val c = pixels[i]; if (Color.alpha(c) > 32) samples += c; i += sampleStep }
        if (samples.isEmpty()) return IntArray(k) { Color.WHITE }
        val rng = Random(42); val centers = IntArray(k); centers[0] = samples[rng.nextInt(samples.size)]
        for (ci in 1 until k) {
            var best = samples[rng.nextInt(samples.size)]; var bestDist = -1
            repeat(min(800, samples.size)) { val c = samples[rng.nextInt(samples.size)]; var nearest = Int.MAX_VALUE; for (j in 0 until ci) nearest = min(nearest, colorDistanceSq(c, centers[j])); if (nearest > bestDist) { bestDist = nearest; best = c } }
            centers[ci] = best
        }
        repeat(10) {
            val sr = LongArray(k); val sg = LongArray(k); val sb = LongArray(k); val count = IntArray(k)
            for (c in samples) { val idx = nearestCenter(c, centers); sr[idx] += Color.red(c); sg[idx] += Color.green(c); sb[idx] += Color.blue(c); count[idx]++ }
            for (j in 0 until k) if (count[j] > 0) centers[j] = Color.rgb((sr[j] / count[j]).toInt(), (sg[j] / count[j]).toInt(), (sb[j] / count[j]).toInt())
        }
        return centers
    }

    private fun assignLabels(pixels: IntArray, centers: IntArray) = IntArray(pixels.size) { nearestCenter(pixels[it], centers) }
    private fun nearestCenter(c: Int, centers: IntArray): Int { var best = 0; var bestD = Int.MAX_VALUE; for (i in centers.indices) { val d = colorDistanceSq(c, centers[i]); if (d < bestD) { bestD = d; best = i } }; return best }
    private fun colorDistanceSq(a: Int, b: Int): Int { val dr = Color.red(a)-Color.red(b); val dg=Color.green(a)-Color.green(b); val db=Color.blue(a)-Color.blue(b); return dr*dr+dg*dg+db*db }

    private fun mergeSmallRegions(input: IntArray, w: Int, h: Int, minSize: Int, centers: IntArray): IntArray {
        val labels=input.copyOf(); val visited=BooleanArray(labels.size); val queue=IntArray(labels.size); val component=IntArray(labels.size); val neighborCounts=IntArray(centers.size)
        for(start in labels.indices){ if(visited[start]) continue; val label=labels[start]; var qh=0; var qt=0; var compSize=0; queue[qt++]=start; visited[start]=true; java.util.Arrays.fill(neighborCounts,0)
            while(qh<qt){ val p=queue[qh++]; component[compSize++]=p; val x=p%w; val y=p/w
                fun see(n:Int){ if(labels[n]==label){ if(!visited[n]){visited[n]=true;queue[qt++]=n} } else neighborCounts[labels[n]]++ }
                if(x>0)see(p-1);if(x+1<w)see(p+1);if(y>0)see(p-w);if(y+1<h)see(p+w) }
            if(compSize<minSize){var replacement=label;var bestTouches=0;for(i in neighborCounts.indices)if(neighborCounts[i]>bestTouches){bestTouches=neighborCounts[i];replacement=i};if(replacement!=label)for(j in 0 until compSize)labels[component[j]]=replacement}
        }; return labels
    }

    private fun drawTemplate(labels:IntArray,w:Int,h:Int):Bitmap{
        val out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val canvas=Canvas(out);canvas.drawColor(Color.WHITE)
        val boundary=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(70,70,70);strokeWidth=max(1f,min(w,h)/850f)}
        for(y in 0 until h)for(x in 0 until w){val p=y*w+x;if(x+1<w&&labels[p]!=labels[p+1])canvas.drawLine((x+1).toFloat(),y.toFloat(),(x+1).toFloat(),(y+1).toFloat(),boundary);if(y+1<h&&labels[p]!=labels[p+w])canvas.drawLine(x.toFloat(),(y+1).toFloat(),(x+1).toFloat(),(y+1).toFloat(),boundary)}
        val seen=BooleanArray(labels.size);val queue=IntArray(labels.size);val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(80,80,80);textAlign=Paint.Align.CENTER;typeface=android.graphics.Typeface.DEFAULT_BOLD}
        for(start in labels.indices){if(seen[start])continue;val label=labels[start];var qh=0;var qt=0;var count=0;var sumX=0L;var sumY=0L;queue[qt++]=start;seen[start]=true
            while(qh<qt){val p=queue[qh++];count++;val x=p%w;val y=p/w;sumX+=x;sumY+=y;fun add(n:Int){if(!seen[n]&&labels[n]==label){seen[n]=true;queue[qt++]=n}};if(x>0)add(p-1);if(x+1<w)add(p+1);if(y>0)add(p-w);if(y+1<h)add(p+w)}
            if(count>=55){val cx=sumX.toFloat()/count;val cy=sumY.toFloat()/count;textPaint.textSize=(sqrt(count.toFloat())*.30f).coerceIn(8f,22f);canvas.drawText((label+1).toString(),cx,cy+textPaint.textSize*.35f,textPaint)}
        };return out
    }

    private fun drawPalette(palette:List<PbnColor>):Bitmap{
        val width=1000;val rowH=92;val height=120+palette.size*rowH;val bmp=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);val canvas=Canvas(bmp);canvas.drawColor(Color.WHITE)
        val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=34f};val title=Paint(text).apply{textSize=44f;typeface=android.graphics.Typeface.DEFAULT_BOLD};canvas.drawText("Палитра картины по номерам",35f,65f,title)
        palette.forEachIndexed{idx,p->val y=105+idx*rowH;val swatch=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=p.color};canvas.drawRoundRect(RectF(35f,y.toFloat(),125f,(y+62).toFloat()),10f,10f,swatch);val hex=String.format("#%06X",0xFFFFFF and p.color);canvas.drawText("${p.number}",155f,(y+45).toFloat(),title);canvas.drawText("$hex   RGB ${Color.red(p.color)}, ${Color.green(p.color)}, ${Color.blue(p.color)}",245f,(y+43).toFloat(),text)};return bmp
    }
}
