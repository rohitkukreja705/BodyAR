package com.hftx.bodyar

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.hftx.bodyar.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var poseAnalyzer: CameraPoseAnalyzer? = null
    private var isFrontCamera = true

    private var currentCategory = ClothingCategory.SHIRT
    private var selectedShirt: ClothingItem? = null
    private var selectedPant: ClothingItem? = null
    private var selectedGlasses: ClothingItem? = null

    private lateinit var adapter: ClothingAdapter

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) handlePickedImage(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupSidebar()
        setupButtons()

        if (hasCameraPermission()) startCamera() else requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // ---------------------------------------------------------------- Camera

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val analyzer = CameraPoseAnalyzer { pose, w, h ->
                runOnUiThread {
                    binding.poseOverlay.updatePose(pose, w, h)
                    binding.txtHint.visibility =
                        if (pose == null) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
            poseAnalyzer = analyzer

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }

            val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            binding.poseOverlay.isFrontCamera = isFrontCamera

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera init failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ---------------------------------------------------------------- Sidebar

    private fun setupSidebar() {
        adapter = ClothingAdapter(
            items = ClothingRepository.itemsFor(this, currentCategory),
            onItemSelected = ::onItemTapped,
            onItemLongPressed = ::onItemLongPressed
        )
        binding.recyclerItems.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerItems.adapter = adapter

        binding.chipShirts.isSelected = true
        binding.chipShirts.setOnClickListener { switchCategory(ClothingCategory.SHIRT) }
        binding.chipPants.setOnClickListener { switchCategory(ClothingCategory.PANT) }
        binding.chipGlasses.setOnClickListener { switchCategory(ClothingCategory.SUNGLASSES) }

        binding.btnCloseDrawer.setOnClickListener { binding.drawerLayout.closeDrawer(Gravity.START) }

        binding.btnClearCategory.setOnClickListener {
            when (currentCategory) {
                ClothingCategory.SHIRT -> { selectedShirt = null; binding.poseOverlay.setSelectedShirt(null) }
                ClothingCategory.PANT -> { selectedPant = null; binding.poseOverlay.setSelectedPant(null) }
                ClothingCategory.SUNGLASSES -> { selectedGlasses = null; binding.poseOverlay.setSelectedGlasses(null) }
            }
            adapter.setSelected(null)
        }

        binding.btnAddPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
    }

    private fun refreshCurrentCategoryList() {
        val selectedId = when (currentCategory) {
            ClothingCategory.SHIRT -> selectedShirt?.id
            ClothingCategory.PANT -> selectedPant?.id
            ClothingCategory.SUNGLASSES -> selectedGlasses?.id
        }
        adapter.submitList(ClothingRepository.itemsFor(this, currentCategory), selectedId)
    }

    private fun switchCategory(category: ClothingCategory) {
        currentCategory = category
        binding.chipShirts.isSelected = category == ClothingCategory.SHIRT
        binding.chipPants.isSelected = category == ClothingCategory.PANT
        binding.chipGlasses.isSelected = category == ClothingCategory.SUNGLASSES
        refreshCurrentCategoryList()
    }

    private fun onItemTapped(item: ClothingItem) {
        when (item.category) {
            ClothingCategory.SHIRT -> {
                selectedShirt = if (selectedShirt?.id == item.id) null else item
                binding.poseOverlay.setSelectedShirt(selectedShirt)
                adapter.setSelected(selectedShirt?.id)
            }
            ClothingCategory.PANT -> {
                selectedPant = if (selectedPant?.id == item.id) null else item
                binding.poseOverlay.setSelectedPant(selectedPant)
                adapter.setSelected(selectedPant?.id)
            }
            ClothingCategory.SUNGLASSES -> {
                selectedGlasses = if (selectedGlasses?.id == item.id) null else item
                binding.poseOverlay.setSelectedGlasses(selectedGlasses)
                adapter.setSelected(selectedGlasses?.id)
            }
        }
    }

    private fun onItemLongPressed(item: ClothingItem) {
        AlertDialog.Builder(this)
            .setTitle("Remove this upload?")
            .setMessage("This will delete \"${item.displayName}\" from your wardrobe.")
            .setPositiveButton("Remove") { _, _ ->
                ClothingRepository.deleteCustomItem(item)
                if (selectedShirt?.id == item.id) { selectedShirt = null; binding.poseOverlay.setSelectedShirt(null) }
                if (selectedPant?.id == item.id) { selectedPant = null; binding.poseOverlay.setSelectedPant(null) }
                if (selectedGlasses?.id == item.id) { selectedGlasses = null; binding.poseOverlay.setSelectedGlasses(null) }
                refreshCurrentCategoryList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handlePickedImage(uri: Uri) {
        val newItem = ClothingRepository.saveCustomItem(this, currentCategory, uri)
        if (newItem == null) {
            Toast.makeText(this, "Couldn't add that image", Toast.LENGTH_SHORT).show()
            return
        }
        refreshCurrentCategoryList()
        Toast.makeText(this, "Added to your ${currentCategory.name.lowercase()} wardrobe", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- Buttons

    private fun setupButtons() {
        binding.btnMenu.setOnClickListener { binding.drawerLayout.openDrawer(Gravity.START) }

        binding.btnClearAll.setOnClickListener {
            selectedShirt = null
            selectedPant = null
            selectedGlasses = null
            binding.poseOverlay.setSelectedShirt(null)
            binding.poseOverlay.setSelectedPant(null)
            binding.poseOverlay.setSelectedGlasses(null)
            adapter.setSelected(null)
        }

        binding.btnCapture.setOnClickListener {
            val capture = imageCapture ?: return@setOnClickListener
            binding.btnCapture.isEnabled = false
            CaptureHelper.captureAndSave(this, capture, binding.poseOverlay) { success, message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    binding.btnCapture.isEnabled = true
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseAnalyzer?.close()
    }
}
