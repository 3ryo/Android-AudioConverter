package com.arun.audioconverter

import android.content.*
import android.database.Cursor
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

data class AudioItem(
    val uri: Uri, val name: String, var checked: Boolean = true,
    var codec: String = "...", var bitrate: String = "...", var sampleRate: String = "...",
    var channels: String = "...", var duration: String = "...", var size: String = "..."
)

class MainActivity : AppCompatActivity() {
    private val items=mutableListOf<AudioItem>()
    private lateinit var list:LinearLayout; private lateinit var status:TextView; private lateinit var progress:ProgressBar
    private lateinit var mp3:CheckBox; private lateinit var wav:CheckBox; private lateinit var wavDepth:Spinner; private lateinit var outLabel:TextView
    private var outputTree:Uri?=null; private val pool=Executors.newSingleThreadExecutor(); private var debugLogUri:Uri?=null
    private val pink=Color.rgb(222,42,143); private val purple=Color.rgb(111,46,184); private val gold=Color.rgb(232,178,29)
    private val blue=Color.rgb(32,139,202); private val green=Color.rgb(89,133,31)

    private val pickFiles=registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){u->u.forEach{addUri(it)}; redraw(); loadMetadata()}
    private val pickInputTree=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){t->if(t!=null){persist(t);scanTree(t);redraw();loadMetadata()}}
    private val pickOutputTree=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){t->if(t!=null){persist(t);outputTree=t;outLabel.text="Output: "+t.toString();pool.execute{try{val r=rootDocument(t);ensureFolder(r,"MP3");ensureFolder(r,"WAV");logDebug("Output folders ready")}catch(e:Throwable){logDebug("Folder init error: ${e.message}")}}}}

    override fun onCreate(b:Bundle?){super.onCreate(b);title="Audio Converter";val v=buildUi();setContentView(v)
        ViewCompat.setOnApplyWindowInsetsListener(v){view,ins->val x=ins.getInsets(WindowInsetsCompat.Type.systemBars());view.setPadding(20+x.left,16+x.top,20+x.right,16+x.bottom);ins}
        ViewCompat.requestApplyInsets(v);initDebugLog();logDebug("App started. Android=${android.os.Build.VERSION.RELEASE}, SDK=${android.os.Build.VERSION.SDK_INT}")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        logDebug("Screen configuration changed: orientation=" + if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait")
    }

    private fun checkTint():ColorStateList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(purple,Color.rgb(70,70,70)))
    private fun panel():GradientDrawable=GradientDrawable().apply{setColor(Color.rgb(249,249,249));cornerRadius=18f;setStroke(2,Color.rgb(205,205,205))}
    private fun button(text:String,color:Int,onClick:()->Unit)=Button(this).apply{this.text=text;setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(color);cornerRadius=16f};setOnClickListener{onClick()};minHeight=48}
    private fun buildUi():View{
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.BLACK)}
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        header.addView(ImageView(this).apply{setImageResource(com.arun.audioconverter.R.drawable.ic_audio_converter_v2);adjustViewBounds=true},LinearLayout.LayoutParams(72,72))
        header.addView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,0,0,0);addView(TextView(this@MainActivity).apply{text="Audio Converter";textSize=25f;setTextColor(Color.WHITE)});addView(TextView(this@MainActivity).apply{text="Powered by FFmpeg  •  Offline conversion";setTextColor(Color.LTGRAY)})},LinearLayout.LayoutParams(0,-2,1f));root.addView(header)
        val stripe=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}; listOf(pink,purple,gold,blue,green).forEach{c->stripe.addView(View(this).apply{setBackgroundColor(c)},LinearLayout.LayoutParams(0,7,1f))};root.addView(stripe)

        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,14,0,10)}
        actions.addView(button("Select Files",pink){pickFiles.launch(arrayOf("audio/*","application/ogg"))},LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(button("Select Folder",purple){pickInputTree.launch(null)},LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(button("Output Folder",blue){pickOutputTree.launch(null)},LinearLayout.LayoutParams(0,-2,1f));root.addView(actions)

        val sel=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        sel.addView(button("Select All",green){items.forEach{it.checked=true};redraw()} ,LinearLayout.LayoutParams(0,-2,1f))
        sel.addView(button("Select None",gold){items.forEach{it.checked=false};redraw()} ,LinearLayout.LayoutParams(0,-2,1f))
        sel.addView(button("Remove",purple){items.removeAll{it.checked};redraw()} ,LinearLayout.LayoutParams(0,-2,1f))
        sel.addView(button("Clear List",pink){items.clear();redraw();progress.progress=0;status.text="Ready.";logDebug("Input list cleared")},LinearLayout.LayoutParams(0,-2,1f));root.addView(sel)

        root.addView(TextView(this).apply{text="  SELECTED AUDIO FILES / PROPERTIES";textSize=15f;setTextColor(purple);setPadding(0,14,0,7)})
        val scroll=ScrollView(this).apply{background=panel()};list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,8,12,8)};scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))

        val formatPanel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=panel();setPadding(14,8,14,8)}
        formatPanel.addView(TextView(this).apply{text="OUTPUT FORMAT";setTextColor(blue)})
        val formats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        mp3=CheckBox(this).apply{text="MP3 320 kbps";isChecked=true;setTextColor(Color.BLACK);buttonTintList=checkTint()};wav=CheckBox(this).apply{text="WAV PCM";setTextColor(Color.BLACK);buttonTintList=checkTint()}
        wavDepth=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("24-bit","16-bit"))}
        formats.addView(mp3);formats.addView(wav);formats.addView(wavDepth);formatPanel.addView(formats);root.addView(formatPanel)
        outLabel=TextView(this).apply{text="Output: choose a folder (MP3 and WAV subfolders are created automatically)";setPadding(8,10,8,10);setTextColor(Color.WHITE)};root.addView(outLabel)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100};root.addView(progress)
        root.addView(button("CONVERT SELECTED FILES",green){convert()},LinearLayout.LayoutParams(-1,-2))
        status=TextView(this).apply{text="Ready.";setPadding(6,10,6,6);setTextColor(Color.WHITE)};root.addView(status);return root
    }

    private fun redraw(){list.removeAllViews();if(items.isEmpty()){list.addView(TextView(this).apply{text="No audio files selected.";setPadding(8,18,8,18);setTextColor(Color.GRAY)});return}
        items.forEachIndexed{idx,a->
            val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(4,6,4,8)}
            row.addView(CheckBox(this).apply{text=a.name;textSize=16f;isChecked=a.checked;setTextColor(Color.BLACK);buttonTintList=checkTint();setOnCheckedChangeListener{_,v->items[idx].checked=v}})
            row.addView(TextView(this).apply{text="Codec: ${a.codec}   •   Bitrate: ${a.bitrate}   •   Sample Rate: ${a.sampleRate}   •   Channels: ${a.channels}\nDuration: ${a.duration}   •   Size: ${a.size}";textSize=13f;setTextColor(Color.DKGRAY);setPadding(48,0,4,4)})
            list.addView(row); if(idx<items.lastIndex)list.addView(View(this).apply{setBackgroundColor(Color.LTGRAY)},LinearLayout.LayoutParams(-1,1))
        }
    }
    private fun loadMetadata(){val pending=items.filter{it.codec=="..."};if(pending.isEmpty())return;pool.execute{pending.forEach{a->try{
        val r=MediaMetadataRetriever();r.setDataSource(this,a.uri)
        val mime=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?:"audio";a.codec=(mime.substringAfter("/").uppercase()).replace("MPEG","MP3")
        val br=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull();a.bitrate=br?.let{"${it/1000} kbps"}?:"—"
        val sr=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toLongOrNull();a.sampleRate=sr?.let{String.format(Locale.US,"%.1f kHz",it/1000.0)}?:"—"
        a.channels=channelInfo(a.uri)
        val ms=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0L;a.duration=String.format(Locale.US,"%02d:%02d:%02d",ms/3600000,(ms/60000)%60,(ms/1000)%60);r.release()
        a.size=fileSize(a.uri)
    }catch(e:Throwable){a.codec=a.name.substringAfterLast(".","").uppercase();logDebug("Metadata error ${a.name}: ${e.message}")};runOnUiThread{redraw()}}}}
    private fun channelInfo(u:Uri):String{try{val ex=MediaExtractor();ex.setDataSource(this,u,null);for(i in 0 until ex.trackCount){val f=ex.getTrackFormat(i);val mime=f.getString(MediaFormat.KEY_MIME)?:"";if(mime.startsWith("audio/")){val n=if(f.containsKey(MediaFormat.KEY_CHANNEL_COUNT))f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)else 0;ex.release();return when(n){1->"Mono (1 ch)";2->"Stereo (2 ch)";0->"—";else->"$n ch"}}};ex.release()}catch(e:Throwable){logDebug("Channel metadata error: ${e.message}")};return "—"}
    private fun fileSize(u:Uri):String{var n=0L;contentResolver.query(u,arrayOf(OpenableColumns.SIZE),null,null,null)?.use{if(it.moveToFirst())n=it.getLong(0)};return if(n>=1048576)String.format(Locale.US,"%.1f MB",n/1048576.0) else String.format(Locale.US,"%.1f KB",n/1024.0)}
    private fun persist(u:Uri){try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}catch(_:Exception){}}
    private fun nameOf(u:Uri):String{var n="audio";contentResolver.query(u,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())n=it.getString(0)?:n};return n}
    private fun addUri(u:Uri){val n=nameOf(u);val e=n.substringAfterLast(".","").lowercase();if(e in listOf("opus","m4a","mp3","flac")&&items.none{it.uri==u})items.add(AudioItem(u,n))}
    private fun scanTree(t:Uri){scanDocumentDir(t,android.provider.DocumentsContract.getTreeDocumentId(t))}
    private fun scanDocumentDir(t:Uri,id:String){val ch=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(t,id);contentResolver.query(ch,arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use{c->while(c.moveToNext()){val cid=c.getString(0);val n=c.getString(1);val mime=c.getString(2);val u=android.provider.DocumentsContract.buildDocumentUriUsingTree(t,cid);if(mime==android.provider.DocumentsContract.Document.MIME_TYPE_DIR)scanDocumentDir(t,cid)else if(n.substringAfterLast(".","").lowercase() in listOf("opus","m4a","mp3","flac")&&items.none{it.uri==u})items.add(AudioItem(u,n))}}}

    private fun initDebugLog(){try{val stamp=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date());val v=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,"AudioConverter_Debug_$stamp.txt");put(MediaStore.Downloads.MIME_TYPE,"text/plain");put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/AudioConverter")};debugLogUri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);logDebug("Debug log created")}catch(_:Throwable){debugLogUri=null}}
    @Synchronized private fun logDebug(m:String){val u=debugLogUri?:return;try{val ts=SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(Date());contentResolver.openOutputStream(u,"wa")?.bufferedWriter()?.use{it.appendLine("[$ts] $m")}}catch(_:Throwable){}}

    private fun convert(){val out=outputTree?:run{Toast.makeText(this,"Choose Output Folder first",Toast.LENGTH_LONG).show();return};val chosen=items.filter{it.checked};if(chosen.isEmpty()){Toast.makeText(this,"Select at least one audio file",Toast.LENGTH_LONG).show();return};if(!mp3.isChecked&&!wav.isChecked){Toast.makeText(this,"Select MP3 and/or WAV output",Toast.LENGTH_LONG).show();return}
        val makeMp3=mp3.isChecked;val makeWav=wav.isChecked;val use16=wavDepth.selectedItem.toString().startsWith("16");progress.progress=0;status.text="Starting conversion...";logDebug("Convert clicked. files=${chosen.size}, mp3=$makeMp3, wav=$makeWav, outputTree=$out")
        pool.execute{chosen.forEachIndexed{i,a->val ext=a.name.substringAfterLast(".","").lowercase();val base=a.name.substringBeforeLast(".");val ti=File(cacheDir,"in_${System.nanoTime()}.$ext");try{contentResolver.openInputStream(a.uri)!!.use{x->ti.outputStream().use{x.copyTo(it)}};val outputs=mutableListOf<Pair<String,String>>();if(makeMp3&&ext in listOf("opus","m4a","flac"))outputs+=Pair("$base.mp3","-c:a libmp3lame -b:a 320k");if(makeWav&&ext in listOf("opus","m4a","mp3","flac"))outputs+=Pair("$base.wav",if(use16)"-c:a pcm_s16le" else "-c:a pcm_s24le")
            outputs.forEach{(name,codec)->val to=File(cacheDir,"out_${System.nanoTime()}.${name.substringAfterLast(".")}");runOnUiThread{status.text="Converting: ${a.name} → $name"};val cmd="-y -i ${q(ti.absolutePath)} -map 0:a:0 -vn $codec ${q(to.absolutePath)}";logDebug("FFmpeg command: $cmd");val s=FFmpegKit.execute(cmd);logDebug("FFmpeg returnCode=${s.returnCode}\n${s.output}");if(ReturnCode.isSuccess(s.returnCode)){val folder=ensureFolder(rootDocument(out),if(name.endsWith(".mp3",true))"MP3" else "WAV");val fn=uniqueName(folder,name);writeToFolder(folder,fn,to);logDebug("Output written: $fn")}else runOnUiThread{status.text="Failed: ${a.name}"};to.delete()}
        }catch(e:Throwable){logDebug("ERROR ${a.name}: ${e.stackTraceToString()}");runOnUiThread{status.text="Error: ${a.name}: ${e.javaClass.simpleName}: ${e.message}"}}finally{ti.delete()};runOnUiThread{progress.progress=(i+1)*100/chosen.size}}
        logDebug("Batch completed");runOnUiThread{status.text="Completed. Debug log: Downloads/AudioConverter";Toast.makeText(this,"Conversion completed",Toast.LENGTH_LONG).show()}}}
    private fun rootDocument(t:Uri):Uri{val id=android.provider.DocumentsContract.getTreeDocumentId(t);return android.provider.DocumentsContract.buildDocumentUriUsingTree(t,id)}
    private fun ensureFolder(p:Uri,n:String):Uri{val id=android.provider.DocumentsContract.getDocumentId(p);val ch=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(p,id);contentResolver.query(ch,arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use{c->while(c.moveToNext())if(c.getString(1)==n&&c.getString(2)==android.provider.DocumentsContract.Document.MIME_TYPE_DIR)return android.provider.DocumentsContract.buildDocumentUriUsingTree(p,c.getString(0))};return android.provider.DocumentsContract.createDocument(contentResolver,p,android.provider.DocumentsContract.Document.MIME_TYPE_DIR,n)?:throw IllegalStateException("Cannot create $n folder")}
    private fun uniqueName(f:Uri,w:String):String{val b=w.substringBeforeLast(".");val e=w.substringAfterLast(".");var n=w;var k=1;while(exists(f,n)){n="${b}_$k.$e";k++};return n}
    private fun exists(f:Uri,n:String):Boolean{val id=android.provider.DocumentsContract.getDocumentId(f);val ch=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(f,id);var z=false;contentResolver.query(ch,arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use{c->while(c.moveToNext())if(c.getString(0)==n){z=true;break}};return z}
    private fun writeToFolder(f:Uri,n:String,x:File){val mime=if(n.endsWith(".mp3",true))"audio/mpeg" else "audio/wav";val u=android.provider.DocumentsContract.createDocument(contentResolver,f,mime,n)?:throw IllegalStateException("Cannot create output file $n");contentResolver.openOutputStream(u,"w")!!.use{o->x.inputStream().use{it.copyTo(o)}}}
    private fun q(s:String)="\"${s.replace("\\","\\\\").replace("\"","\\\"")}\""
}
