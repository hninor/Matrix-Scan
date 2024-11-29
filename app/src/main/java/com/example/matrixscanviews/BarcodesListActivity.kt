package com.example.matrixscanviews

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.matrixscanviews.ui.theme.MatrixScanViewsTheme
import com.google.gson.Gson

class BarcodesListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = intent.getStringExtra("barcodes")
        val barcodeList = Gson().fromJson(json, Array<Barcode>::class.java).asList()
        enableEdgeToEdge()
        setContent {
            MatrixScanViewsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BarcodeList(
                        barcodeList,
                        modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}


data class Barcode(val type: String, val value: String)


@Composable
fun BarcodeList(barcodes: List<Barcode>, modifier: Modifier) {


    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
    ) {
        items(barcodes) {
            BarcodeItem(it)
        }

    }


}

@Composable
fun BarcodeItem(barcode: Barcode) {

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = barcode.type,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = barcode.value
            )
        }

    }

}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MatrixScanViewsTheme {
        Greeting("Android")
    }
}