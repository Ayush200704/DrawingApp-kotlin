package com.example.kidsdrawingapp

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private var drawingView : DrawingView? = null
    private var imageButtonCurrentPaint : ImageButton? = null
    private var customProgressBar : Dialog? = null

    private val galleryLauncher : ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        result->
        if(result.resultCode == RESULT_OK && result.data!= null){
            val galleryImageView : ImageView = findViewById(R.id.iv_background)
            galleryImageView.setImageURI(result.data?.data)
        }
    }

    private val ibSaveResultLauncher : ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
        permissions->
        for(i in permissions){
            val permissionName = i.key
            val isGranted = i.value
            if(isGranted){
                if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                    val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    galleryLauncher.launch(galleryIntent)
                }
                else if(permissionName == Manifest.permission.WRITE_EXTERNAL_STORAGE){
                    Toast.makeText(this, "saving the image", Toast.LENGTH_LONG).show()
                }
            }else{
                if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                    Snackbar.make(findViewById(android.R.id.content), "external storage access denied", Snackbar.LENGTH_LONG ).show()
                    //Toast.makeText(this, "external storage access denied", Toast.LENGTH_LONG).show()
                }else{
                    Snackbar.make(findViewById(android.R.id.content), "external storage access denied", Snackbar.LENGTH_LONG ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.0f)
        val brushSize : ImageButton = findViewById(R.id.ib_brushSize)
        val btnUndo : ImageButton = findViewById(R.id.ib_undo)
        val ibGallery : ImageButton = findViewById(R.id.ib_gallery)
        val btnRedo : ImageButton = findViewById(R.id.ib_redo)
        val ibSave : ImageButton = findViewById(R.id.ib_save)

        brushSize.setOnClickListener{
            showBrushSizeDialog()
        }
        val llPallet = findViewById<LinearLayout>(R.id.ll_paint_pallet)
        imageButtonCurrentPaint = llPallet[2] as ImageButton
        imageButtonCurrentPaint!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))


        ibGallery.setOnClickListener{
            if(shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)){
                alertDialog("Read external storage access is denied", "drawing app need the access to work properly")
            }
            else if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                alertDialog("write external storage access is denied", "drawing app need the access to work properly")
            }
            else{
                ibSaveResultLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }

        ibSave.setOnClickListener{
            if(isReadStorageAllowed()){
                customProgressBar()
                lifecycleScope.launch {
                    saveBitmapFile(getBitmapFromView(findViewById(R.id.fl_background_layer)))
                }
            }

        }

        btnRedo.setOnClickListener{
            drawingView?.redoFun()
        }

        btnUndo.setOnClickListener{
            drawingView?.undoFun()

        }

    }

    private fun isReadStorageAllowed(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun showBrushSizeDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")

        val btnSmall : ImageButton = brushDialog.findViewById(R.id.ib_small)
        btnSmall.setOnClickListener{
            drawingView?.setSizeForBrush(10.0f)
            brushDialog.dismiss()
        }

        val btnMedium : ImageButton = brushDialog.findViewById(R.id.ib_medium)
        btnMedium.setOnClickListener{
            drawingView?.setSizeForBrush(20.0f)
            brushDialog.dismiss()
        }

        val btnLarge : ImageButton = brushDialog.findViewById(R.id.ib_large)
        btnLarge.setOnClickListener{
            drawingView?.setSizeForBrush(30.0f)
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintPressed(view : View){
        val imageButton = view as ImageButton
        val imageButtonTag = imageButton.tag.toString()
        if(view != imageButtonCurrentPaint){
            drawingView?.colorChange(imageButtonTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed))

            imageButtonCurrentPaint!!.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal))

            imageButtonCurrentPaint = view
        }

    }

    private fun alertDialog(title: String, message: String){
        val builder : AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Cancel"){
            Dialog, _-> Dialog.dismiss()
        }
        builder.create()
        builder.show()
    }

    private fun getBitmapFromView(view: View): Bitmap{
        val bitmap : Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgImage = view.background

        if(bgImage != null){
            bgImage.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return bitmap
    }

    private suspend fun saveBitmapFile(bitmap : Bitmap): String{
        var result = ""
        withContext(Dispatchers.IO){
            if(bitmap != null){
                try {
                    val byte = ByteArrayOutputStream()

                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, byte)

                    val f = File(externalCacheDir?.absoluteFile.toString() + File.separator + "kidDrawingApp_"+ System.currentTimeMillis()/1000 + ".png")
                    val fo = FileOutputStream(f)
                    fo.write(byte.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread{
                        cancelCustomProgressBar()
                        if(result.isNotEmpty()){
                            Toast.makeText(this@MainActivity, "File has been saved successfully: $result", Toast.LENGTH_SHORT).show()
                            shareImage(result)
                        }else{
                            Toast.makeText(this@MainActivity, "Something Went Wrong While Saving The File", Toast.LENGTH_SHORT).show()
                        }
                    }
                }catch (e: Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun shareImage(result: String){
        MediaScannerConnection.scanFile(this@MainActivity, arrayOf(result),null){
            path, uri->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "share"))
        }
    }

    private fun customProgressBar(){
        customProgressBar = Dialog(this@MainActivity)
        customProgressBar?.setContentView(R.layout.progress_custom_bar)
        customProgressBar?.show()
    }

    private fun cancelCustomProgressBar(){
        if(customProgressBar != null){
            customProgressBar?.dismiss()
            customProgressBar = null
        }
    }

}