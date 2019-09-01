package pranay.com.cameraxapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import coil.api.load
import kotlinx.android.synthetic.main.activity_preview.*
import java.io.File

class PreviewActivity : AppCompatActivity() {

    companion object{
        const val FILE_PATH="FILE_PATH"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        imageViewPreview.load(File(intent.extras?.getString(FILE_PATH)))

    }
}
