package com.example.myqrapp;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.RGBLuminanceSource; // ✅ Correct import
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private EditText editInput;
    private ImageView imageQR;
    private TextView textResult;
    private Switch switchDark;

    private Bitmap currentQRBitmap;

    private static final String PREFS = "qr_prefs";
    private static final String KEY_DARK = "dark_mode";

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initLaunchers();
        setupButtons();
        setupDarkModeSwitch();
    }

    private void applySavedTheme() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean dark = sp.getBoolean(KEY_DARK, false);
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    private void initViews() {
        editInput = findViewById(R.id.editInput);
        imageQR = findViewById(R.id.imageQR);
        textResult = findViewById(R.id.textResult);
        switchDark = findViewById(R.id.switchDark);
    }

    private void initLaunchers() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            decodeQRFromGallery(uri);
                        } else {
                            Toast.makeText(MainActivity.this, "No image selected", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        cameraPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                        isGranted -> {
                            if (isGranted) {
                                startCameraScan();
                            } else {
                                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                            }
                        });
    }

    private void setupButtons() {
        Button btnGenerate = findViewById(R.id.btnGenerate);
        Button btnCopy = findViewById(R.id.btnCopy);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnShare = findViewById(R.id.btnShare);
        Button btnScanCamera = findViewById(R.id.btnScanCamera);
        Button btnScanGallery = findViewById(R.id.btnScanGallery);

        btnGenerate.setOnClickListener(v -> generateQR());
        btnCopy.setOnClickListener(v -> copyCurrentText());
        btnSave.setOnClickListener(v -> saveCurrentQR());
        btnShare.setOnClickListener(v -> shareCurrentQR());
        btnScanCamera.setOnClickListener(v -> requestCameraAndScan());
        btnScanGallery.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
    }

    private void setupDarkModeSwitch() {
        boolean dark = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DARK, false);
        switchDark.setChecked(dark);
        switchDark.setOnCheckedChangeListener((button, isChecked) -> {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_DARK, isChecked)
                    .apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
        });
    }

    private void generateQR() {
        String text = editInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Enter text first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            currentQRBitmap = encoder.encodeBitmap(text, BarcodeFormat.QR_CODE, 600, 600);
            imageQR.setImageBitmap(currentQRBitmap);
            textResult.setText("(Generated) " + text);
        } catch (WriterException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error creating QR", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyCurrentText() {
        String text = editInput.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("qr_text", text));
        Toast.makeText(this, "Text copied", Toast.LENGTH_SHORT).show();
    }

    private void saveCurrentQR() {
        if (currentQRBitmap == null) {
            Toast.makeText(this, "Generate a QR first", Toast.LENGTH_SHORT).show();
            return;
        }
        saveBitmapToGallery(currentQRBitmap);
    }

    private void saveBitmapToGallery(Bitmap bitmap) {
        String fileName = "QR_" + System.currentTimeMillis() + ".png";
        try {
            OutputStream fos;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/QR Codes");
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                fos = resolver.openOutputStream(uri);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                if (fos != null) fos.close();
                Toast.makeText(this, "Saved to gallery", Toast.LENGTH_SHORT).show();
            } else {
                String path = MediaStore.Images.Media.insertImage(
                        getContentResolver(), bitmap, fileName, "QR Code");
                if (path == null) {
                    Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Saved to gallery", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCurrentQR() {
        if (currentQRBitmap == null) {
            Toast.makeText(this, "Generate a QR first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String path = MediaStore.Images.Media.insertImage(
                    getContentResolver(), currentQRBitmap, "QR_Share", "Shared QR");
            Uri uri = Uri.parse(path);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Here is a QR code I generated.");
            startActivity(Intent.createChooser(shareIntent, "Share QR Code"));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestCameraAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCameraScan();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCameraScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("Point camera at a QR code");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    private void decodeQRFromGallery(Uri uri) {
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            String decoded = decodeBitmap(bitmap);
            if (decoded != null) {
                editInput.setText(decoded);
                textResult.setText("(Decoded) " + decoded);
                Toast.makeText(this, "QR decoded", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No QR found in image", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error reading image", Toast.LENGTH_SHORT).show();
        }
    }

    private String decodeBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            Result result = new MultiFormatReader().decode(binaryBitmap);
            return result.getText();
        } catch (NotFoundException e) {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result =
                IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
            } else {
                editInput.setText(result.getContents());
                textResult.setText("(Scanned) " + result.getContents());
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
