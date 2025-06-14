package com.example.glean.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.glean.R;
import com.example.glean.activity.SkinSelectionActivity;
import com.example.glean.adapter.BadgeAdapter;
import com.example.glean.adapter.ProfileBadgeAdapter;
import com.example.glean.databinding.FragmentProfileBinding;
import com.example.glean.databinding.DialogEditProfileBinding;
import com.example.glean.databinding.DialogSettingsBinding;
import com.example.glean.db.AppDatabase;
import com.example.glean.model.Badge;
import com.example.glean.model.UserEntity;
import com.example.glean.util.PasswordValidator;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileFragment extends Fragment {    private static final String TAG = "ProfileFragment";    private static final int PICK_IMAGE_REQUEST = 1001;
    private static final int CAMERA_REQUEST = 1002;
    private static final int REQUEST_STORAGE_PERMISSION = 1003;
    private static final int REQUEST_CAMERA_PERMISSION = 1004;
    private static final int SKIN_SELECTION_REQUEST = 1005;

    private FragmentProfileBinding binding;
    
    private AppDatabase db;
    private int userId;
    private UserEntity currentUser;
    private ExecutorService executor;
    private Uri selectedImageUri;
    private Uri cameraImageUri;
    private String profileImagePath;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = AppDatabase.getInstance(requireContext());
        
        // Get user ID from SharedPreferences with better error handling
        SharedPreferences prefs = requireActivity().getSharedPreferences("USER_PREFS", 0);
        userId = prefs.getInt("USER_ID", -1);
        
        // Fallback to default preferences if not found
        if (userId == -1) {
            SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            userId = defaultPrefs.getInt("USER_ID", -1);
        }
        
        executor = Executors.newSingleThreadExecutor();
        
        Log.d(TAG, "ProfileFragment created with userId: " + userId);
    }    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            binding = FragmentProfileBinding.inflate(inflater, container, false);
            Log.d(TAG, "Profile binding created successfully");
            return binding.getRoot();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create profile binding, using fallback", e);
            return createFallbackLayout(inflater, container);
        }
    }
    
    private View createFallbackLayout(LayoutInflater inflater, ViewGroup container) {
        // Create a simple fallback layout if the main layout fails
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        // Title
        TextView titleText = new TextView(requireContext());
        titleText.setText("Profil");
        titleText.setTextSize(24);
        titleText.setGravity(android.view.Gravity.CENTER);
        titleText.setPadding(0, 0, 0, 24);
        layout.addView(titleText);
        
        // User info
        TextView nameText = new TextView(requireContext());
        nameText.setText(getString(R.string.loading));
        nameText.setTextSize(18);
        nameText.setGravity(android.view.Gravity.CENTER);
        layout.addView(nameText);
        
        TextView emailText = new TextView(requireContext());
        emailText.setText("Loading email...");
        emailText.setTextSize(14);
        emailText.setGravity(android.view.Gravity.CENTER);
        emailText.setPadding(0, 8, 0, 24);
        layout.addView(emailText);
        
        // Simple button for basic functionality
        Button editButton = new Button(requireContext());
        editButton.setText("Edit Profile");
        editButton.setOnClickListener(v -> showEditProfileDialog());
        layout.addView(editButton);
        
        scrollView.addView(layout);
        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
          try {
            setupUI();
            loadUserData();
            updateProfileSkin(); // Apply current skin
        } catch (Exception e) {
            Log.e(TAG, "Error setting up ProfileFragment", e);
            Toast.makeText(requireContext(), getString(R.string.error_loading_profile), Toast.LENGTH_SHORT).show();
        }
    }
      private void setupUI() {
        // Setup RecyclerView for badges with same design as Customize Profile
        if (binding.rvBadges != null) {
            binding.rvBadges.setLayoutManager(new GridLayoutManager(requireContext(), 3)); // Match CustomizeProfile 3-column layout
        }
        
        // Set click listeners with safe null checks
        setupClickListeners();
        
        // Add some visual feedback
        addVisualFeedback();
    }
    
    private void setupClickListeners() {
        // Edit Profile button
        if (binding.btnEditProfile != null) {
            binding.btnEditProfile.setOnClickListener(v -> {
                Log.d(TAG, "Edit profile button clicked");
                showEditProfileDialog();
            });
        }
        
        // Settings button
        if (binding.btnSettings != null) {
            binding.btnSettings.setOnClickListener(v -> {
                Log.d(TAG, "Settings button clicked");
                showSettingsDialog();
            });
        }
        
        // Logout button
        if (binding.btnLogout != null) {
            binding.btnLogout.setOnClickListener(v -> {
                Log.d(TAG, "Logout button clicked");
                showLogoutConfirmation();
            });
        }
          // Profile picture click
        if (binding.ivProfilePic != null) {
            binding.ivProfilePic.setOnClickListener(v -> {
                Log.d(TAG, "Profile picture clicked");
                checkPermissionsAndSelectImage();
            });        }
          // Customize Profile button
        if (binding.btnCustomize != null) {
            binding.btnCustomize.setOnClickListener(v -> {
                Log.d(TAG, "Customize profile button clicked");
                openCustomizeProfileActivity();
            });
        }
        
        // Settings icon in header
        if (binding.btnSettings != null) {
            binding.btnSettings.setOnClickListener(v -> {
                Log.d(TAG, "Settings icon clicked");
                showSettingsDialog();
            });
        }        // Theme toggle (if exists)
        setupThemeToggle();
    }
      private void addVisualFeedback() {        // Add ripple effect and elevation feedback
    View[] clickableViews = {
        binding.btnEditProfile,
        binding.btnSettings,
        binding.btnLogout,
        binding.ivProfilePic
    };
        
        for (View view : clickableViews) {
            if (view != null) {
                view.setOnTouchListener((v, event) -> {
                    switch (event.getAction()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            v.setAlpha(0.8f);
                            v.setScaleX(0.98f);
                            v.setScaleY(0.98f);
                            break;
                        case android.view.MotionEvent.ACTION_UP:
                        case android.view.MotionEvent.ACTION_CANCEL:
                            v.animate()
                                .alpha(1.0f)
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(150)
                                .start();
                            break;
                    }
                    return false; // Don't consume the event
                });
            }
        }
    }
    
    private void setupThemeToggle() {
        // Try to find theme toggle switch
        View themeToggle = findViewById("switchTheme", "btnTheme", "toggleTheme");
        if (themeToggle != null) {
            themeToggle.setOnClickListener(v -> toggleTheme());
        }
    }
    
    private View findViewById(String... possibleIds) {
        for (String id : possibleIds) {
            try {
                int resId = getResources().getIdentifier(id, "id", requireContext().getPackageName());
                if (resId != 0) {
                    View view = binding.getRoot().findViewById(resId);
                    if (view != null) {
                        return view;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "View not found with id: " + id);
            }
        }
        return null;
    }
    
    private void loadUserData() {
        if (userId == -1) {
            Log.e(TAG, "Invalid user ID, cannot load user data");
            Toast.makeText(requireContext(), "User not found. Please login again.", Toast.LENGTH_LONG).show();
            logout();
            return;
        }
        
        Log.d(TAG, "Loading user data for userId: " + userId);
          db.userDao().getUserById(userId).observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                String username = user.getUsername() != null ? user.getUsername() : "Unknown User";
                Log.d(TAG, "User data loaded successfully: " + username);
                currentUser = user;
                updateUIWithUserData(user);
            } else {
                Log.e(TAG, "User not found in database for userId: " + userId);
                // Try to create a default user
                createDefaultUserIfNeeded();
            }
        });
    }
      private void updateUIWithUserData(UserEntity user) {
        try {
            // Set user info with safe null checks
            String displayName = getDisplayName(user);
            if (binding.tvName != null) {
                binding.tvName.setText(displayName);
            }
            
            if (binding.tvEmail != null) {
                String email = user.getEmail();
                binding.tvEmail.setText(email != null && !email.isEmpty() ? email : "No email");
            }
            
            // Format and display member since date
            String memberSince = formatMemberSince(user.getCreatedAt());
            if (binding.tvMemberSince != null) {
                binding.tvMemberSince.setText("Member since: " + memberSince);
            }
            
            // Display points with safe handling
            if (binding.tvTotalPoints != null) {
                binding.tvTotalPoints.setText(String.valueOf(user.getPoints()));
            }
            
            // Load profile image
            loadProfileImage(user);
            
            // Load user statistics
            loadUserStatistics();
            
            // Setup badges
            setupBadges(user);
            
            // Update profile decorations
            updateProfileDecorations(user);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI with user data", e);
            // Set default values on error
            setDefaultUIValues();
        }
    }
    
    private void setDefaultUIValues() {
        try {
            if (binding.tvName != null) {
                binding.tvName.setText("User");
            }
            if (binding.tvEmail != null) {
                binding.tvEmail.setText("No email");
            }
            if (binding.tvMemberSince != null) {
                binding.tvMemberSince.setText("Member since: Recently");
            }
            if (binding.tvTotalPoints != null) {
                binding.tvTotalPoints.setText("0");
            }
            if (binding.tvTotalPlogs != null) {
                binding.tvTotalPlogs.setText("0");
            }
            if (binding.tvTotalDistance != null) {
                binding.tvTotalDistance.setText("0.0");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting default UI values", e);
        }
    }
    
    private void loadProfileImage(UserEntity user) {
        profileImagePath = user.getProfileImagePath();
        
        if (binding.ivProfilePic != null) {
            if (profileImagePath != null && !profileImagePath.isEmpty()) {
                // Load from file path
                File imageFile = new File(profileImagePath);
                if (imageFile.exists()) {
                    Glide.with(this)
                            .load(imageFile)
                            .placeholder(android.R.drawable.ic_menu_camera)
                            .error(android.R.drawable.ic_menu_camera)
                            .circleCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.ivProfilePic);
                } else {
                    // File doesn't exist, load default
                    loadDefaultProfileImage();
                }
            } else {
                // No profile image set, load default
                loadDefaultProfileImage();
            }
        }
    }
    
    private void loadDefaultProfileImage() {
        if (binding.ivProfilePic != null) {
            Glide.with(this)
                    .load(android.R.drawable.ic_menu_camera)
                    .circleCrop()
                    .into(binding.ivProfilePic);
        }
    }
    
    private String getDisplayName(UserEntity user) {
        if (user.getFirstName() != null && !user.getFirstName().isEmpty()) {
            String fullName = user.getFirstName();
            if (user.getLastName() != null && !user.getLastName().isEmpty()) {
                fullName += " " + user.getLastName();
            }
            return fullName;
        } else if (user.getUsername() != null && !user.getUsername().isEmpty()) {
            return user.getUsername();
        } else if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            return user.getEmail().split("@")[0];
        }
        return "User";
    }
    
    private String formatMemberSince(long createdAt) {
        if (createdAt > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
            return sdf.format(new Date(createdAt));
        }
        return "Recently";
    }
    
    private void loadUserStatistics() {
        if (userId == -1) return;
        
        executor.execute(() -> {
            try {
                // Get user statistics from database
                int totalRecords = db.recordDao().getRecordCountByUserId(userId);
                float totalDistance = db.recordDao().getTotalDistanceByUserId(userId);
                long totalDuration = db.recordDao().getTotalDurationByUserId(userId);
                  requireActivity().runOnUiThread(() -> {
                    // Update UI with safe null checks
                    if (binding.tvTotalPoints != null) {
                        // Show user's total accumulated points instead of record count
                        int userPoints = currentUser != null ? currentUser.getPoints() : 0;
                        binding.tvTotalPoints.setText(String.valueOf(userPoints));
                    }
                    if (binding.tvTotalPlogs != null) {
                        binding.tvTotalPlogs.setText(String.valueOf(totalRecords));
                    }
                    // Fixed: Now properly referencing the correct TextView ID
                    if (binding.tvTotalDistance != null) {
                        binding.tvTotalDistance.setText(String.format(Locale.getDefault(), "%.1f", totalDistance / 1000f));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading user statistics", e);                requireActivity().runOnUiThread(() -> {
                    // Set default values on error
                    if (binding.tvTotalPoints != null) {
                        binding.tvTotalPoints.setText("0");
                    }
                    if (binding.tvTotalPlogs != null) {
                        binding.tvTotalPlogs.setText("0");
                    }
                    if (binding.tvTotalDistance != null) {
                        binding.tvTotalDistance.setText("0.0");
                    }
                });
            }
        });
    }
      private void setupBadges(UserEntity user) {
        List<Badge> badges = generateUserBadges(user);
          if (binding.rvBadges != null) {            // Use the new ProfileBadgeAdapter
            ProfileBadgeAdapter adapter = new ProfileBadgeAdapter(requireContext(), badges, badge -> {
                // Navigate to Profile Customization when badge is clicked
                openCustomizeProfileActivity();
            });
            binding.rvBadges.setAdapter(adapter);
        }
    }
    
    // Generate badges using the same logic as BadgeSelectionFragment for consistency
    private List<Badge> generateUserBadges(UserEntity user) {
        List<Badge> badges = new ArrayList<>();
        int points = user.getPoints();
        int badgeIdCounter = 1;
        
        // Always available badges
        Badge starter = new Badge(badgeIdCounter++, "Starter", "Your first badge", "starter", 1, true);
        starter.setIconResource(R.drawable.ic_star);
        badges.add(starter);
        
        // Achievement-based badges (same logic as BadgeSelectionFragment)
        if (points >= 50) {
            Badge greenHelper = new Badge(badgeIdCounter++, "Green Helper", "Eco-friendly contributor", "green_helper", 1, true);
            greenHelper.setIconResource(R.drawable.ic_leaf);
            badges.add(greenHelper);
        }
        if (points >= 100) {
            Badge ecoWarrior = new Badge(badgeIdCounter++, "Eco Warrior", "Environmental champion", "eco_warrior", 2, true);
            ecoWarrior.setIconResource(R.drawable.ic_award);
            badges.add(ecoWarrior);
        }
        if (points >= 200) {
            Badge greenChampion = new Badge(badgeIdCounter++, "Green Champion", "Green environmental champion", "green_champion", 2, true);
            greenChampion.setIconResource(R.drawable.ic_award);
            badges.add(greenChampion);
        }
        if (points >= 500) {
            Badge earthGuardian = new Badge(badgeIdCounter++, "Earth Guardian", "Protector of the environment", "earth_guardian", 3, true);
            earthGuardian.setIconResource(R.drawable.ic_globe);
            badges.add(earthGuardian);
        }
        if (points >= 1000) {
            Badge expertPlogger = new Badge(badgeIdCounter++, "Expert Plogger", "Master of plogging", "expert_plogger", 3, true);
            expertPlogger.setIconResource(R.drawable.ic_crown);
            badges.add(expertPlogger);
        }
        if (points >= 2000) {
            Badge ecoLegend = new Badge(badgeIdCounter++, "Eco Legend", "Legendary environmental hero", "eco_legend", 3, true);
            ecoLegend.setIconResource(R.drawable.ic_crown);
            badges.add(ecoLegend);
        }
        
        // Special badges
        if (points >= 1500) {
            Badge masterCleaner = new Badge(badgeIdCounter++, "Master Cleaner", "Expert in cleanup activities", "master_cleaner", 3, true);
            masterCleaner.setIconResource(R.drawable.ic_cleaning);
            badges.add(masterCleaner);
        }
        
        return badges;
    }
    
    private void updateProfileDecorations(UserEntity user) {
        if (binding.ivProfileFrame == null) return;
        
        String activeDecoration = user.getActiveDecoration();
        if (activeDecoration != null && !activeDecoration.isEmpty() && !activeDecoration.equals("none")) {
            try {
                switch (activeDecoration) {
                    case "1": // Gold frame
                        binding.ivProfileFrame.setImageResource(android.R.drawable.star_big_on);
                        binding.ivProfileFrame.setVisibility(View.VISIBLE);
                        break;
                    case "2": // Silver frame
                        binding.ivProfileFrame.setImageResource(android.R.drawable.star_on);
                        binding.ivProfileFrame.setVisibility(View.VISIBLE);
                        break;
                    case "3": // Bronze frame
                        binding.ivProfileFrame.setImageResource(android.R.drawable.star_off);
                        binding.ivProfileFrame.setVisibility(View.VISIBLE);
                        break;
                    default:
                        binding.ivProfileFrame.setVisibility(View.GONE);
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading profile decoration", e);
                binding.ivProfileFrame.setVisibility(View.GONE);
            }
        } else {
            binding.ivProfileFrame.setVisibility(View.GONE);
        }
    }
      private void checkPermissionsAndSelectImage() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
        } else {
            showImageSourceDialog();
        }
    }
    
    private void showImageSourceDialog() {
        String[] options = {"Camera", "Gallery"};
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Profile Picture")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Camera option
                        checkCameraPermissionAndCapture();
                    } else {
                        // Gallery option
                        selectImageFromGallery();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void checkCameraPermissionAndCapture() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            captureImageFromCamera();
        }
    }
    
    private void captureImageFromCamera() {
        try {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (cameraIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                // Create image file
                File photoFile = createImageFile();
                if (photoFile != null) {
                    cameraImageUri = FileProvider.getUriForFile(
                            requireContext(),
                            "com.example.glean.fileprovider",
                            photoFile
                    );
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                    startActivityForResult(cameraIntent, CAMERA_REQUEST);
                }
            } else {
                Toast.makeText(requireContext(), "No camera app available", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing image from camera", e);
            Toast.makeText(requireContext(), "Error opening camera", Toast.LENGTH_SHORT).show();
        }
    }
    
    private File createImageFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "PROFILE_" + timeStamp + "_";
            File storageDir = new File(requireContext().getFilesDir(), "images");
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (Exception e) {
            Log.e(TAG, "Error creating image file", e);
            return null;
        }
    }
    
    private void selectImageFromGallery() {
        try {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Profile Picture"), PICK_IMAGE_REQUEST);
        } catch (Exception e) {
            Log.e(TAG, "Error selecting image from gallery", e);
            Toast.makeText(requireContext(), "Error opening image picker", Toast.LENGTH_SHORT).show();
        }
    }    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode != Activity.RESULT_OK) {
            return;        }
          try {            
            // Handle skin selection
            if (requestCode == SKIN_SELECTION_REQUEST && data != null) {
                String selectedSkin = data.getStringExtra("selected_skin");
                if (selectedSkin != null) {
                    updateProfileSkin();
                    Toast.makeText(requireContext(), "Profile skin updated!", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            
            // Handle image selection and cropping
            if (requestCode == PICK_IMAGE_REQUEST && data != null && data.getData() != null) {
                // Gallery image selected
                selectedImageUri = data.getData();
                startCropActivity(selectedImageUri);
                
            } else if (requestCode == CAMERA_REQUEST && cameraImageUri != null) {
                // Camera image captured
                startCropActivity(cameraImageUri);
                
            } else if (requestCode == UCrop.REQUEST_CROP) {
                // Image cropped successfully
                handleCropResult(data);
                
            } else if (requestCode == UCrop.RESULT_ERROR) {
                // Crop error
                handleCropError(data);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling activity result", e);
            Toast.makeText(requireContext(), "Error processing image", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void startCropActivity(Uri sourceUri) {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "cropped_profile_" + timeStamp + ".jpg";
            Uri destinationUri = Uri.fromFile(new File(requireContext().getCacheDir(), fileName));
            
            UCrop.Options options = new UCrop.Options();
            options.setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG);
            options.setCompressionQuality(90);
            options.setHideBottomControls(false);
            options.setFreeStyleCropEnabled(true);
            options.setShowCropFrame(true);
            options.setShowCropGrid(true);
            options.setCircleDimmedLayer(true);
            options.setCropGridStrokeWidth(2);
            options.setCropFrameStrokeWidth(4);
            options.setMaxBitmapSize(1024);
              // Set colors
            options.setToolbarColor(ContextCompat.getColor(requireContext(), R.color.primary));
            options.setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.primary_dark));
            options.setActiveControlsWidgetColor(ContextCompat.getColor(requireContext(), R.color.accent));
            
            UCrop.of(sourceUri, destinationUri)
                    .withAspectRatio(1, 1)
                    .withMaxResultSize(512, 512)
                    .withOptions(options)
                    .start(requireContext(), this);
                    
        } catch (Exception e) {
            Log.e(TAG, "Error starting crop activity", e);
            Toast.makeText(requireContext(), "Error opening crop editor", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleCropResult(@Nullable Intent data) {
        if (data != null) {
            final Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                selectedImageUri = resultUri;
                
                // Display the cropped image
                if (binding != null && binding.ivProfilePic != null) {
                    Glide.with(this)
                            .load(resultUri)
                            .placeholder(android.R.drawable.ic_menu_camera)
                            .error(android.R.drawable.ic_menu_camera)
                            .circleCrop()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .into(binding.ivProfilePic);
                }
                
                // Save the cropped image
                saveProfileImage();
                
                Toast.makeText(requireContext(), "Profile picture updated successfully", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void handleCropError(@Nullable Intent data) {
        if (data != null) {
            final Throwable cropError = UCrop.getError(data);
            if (cropError != null) {
                Log.e(TAG, "Crop error: " + cropError.getMessage(), cropError);
                Toast.makeText(requireContext(), "Error cropping image: " + cropError.getMessage(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Error cropping image", Toast.LENGTH_SHORT).show();
            }
        }
    }
      private void saveProfileImage() {
        if (selectedImageUri != null && currentUser != null) {
            executor.execute(() -> {
                try {
                    // Copy the cropped image to permanent storage
                    String permanentImagePath = copyImageToPermanentStorage(selectedImageUri);
                    
                    if (permanentImagePath != null) {
                        // Update user with new image path
                        currentUser.setProfileImagePath(permanentImagePath);
                        db.userDao().update(currentUser);
                        
                        requireActivity().runOnUiThread(() -> {
                            // Reload the profile image
                            loadProfileImage();
                            Toast.makeText(requireContext(), "Profile picture updated!", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Failed to save profile picture", Toast.LENGTH_SHORT).show();
                        });
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error saving profile image to database", e);
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Failed to save profile picture", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }
    
    private String copyImageToPermanentStorage(Uri sourceUri) {
        try {
            // Create permanent storage directory
            File profileImagesDir = new File(requireContext().getFilesDir(), "profile_images");
            if (!profileImagesDir.exists()) {
                profileImagesDir.mkdirs();
            }
            
            // Create unique filename
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "profile_" + userId + "_" + timeStamp + ".jpg";
            File destinationFile = new File(profileImagesDir, fileName);
            
            // Copy file from source URI to destination
            try (java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(sourceUri);
                 java.io.FileOutputStream outputStream = new java.io.FileOutputStream(destinationFile)) {
                
                if (inputStream != null) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                    outputStream.flush();
                    
                    Log.d(TAG, "Image saved to: " + destinationFile.getAbsolutePath());
                    return destinationFile.getAbsolutePath();
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error copying image to permanent storage", e);
        }
        return null;
    }
    
    private void loadProfileImage() {
        if (currentUser != null && currentUser.getProfileImagePath() != null && binding != null && binding.ivProfilePic != null) {
            String imagePath = currentUser.getProfileImagePath();
            
            // Clear any existing cache for this image
            Glide.with(requireContext()).clear(binding.ivProfilePic);
            
            // Load the image with cache busting
            Glide.with(this)
                    .load(new File(imagePath))
                    .placeholder(android.R.drawable.ic_menu_camera)
                    .error(android.R.drawable.ic_menu_camera)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .signature(new com.bumptech.glide.signature.ObjectKey(System.currentTimeMillis()))
                    .into(binding.ivProfilePic);
            
            Log.d(TAG, "Loading profile image from: " + imagePath);
        } else {
            // Load default profile image
            if (binding != null && binding.ivProfilePic != null) {
                Glide.with(this)
                        .load(android.R.drawable.ic_menu_camera)
                        .circleCrop()
                        .into(binding.ivProfilePic);
            }
        }
    }
    
    private void showEditProfileDialog() {
        try {
            BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
            DialogEditProfileBinding dialogBinding = DialogEditProfileBinding.inflate(getLayoutInflater());
            dialog.setContentView(dialogBinding.getRoot());
              // Set current values
            if (currentUser != null) {
                String currentName = getDisplayName(currentUser);
                dialogBinding.etName.setText(currentName);
                  // Show email as read-only (disable editing)
                if (currentUser.getEmail() != null) {
                    dialogBinding.etEmail.setText(currentUser.getEmail());
                    dialogBinding.etEmail.setEnabled(false); // Make email field read-only
                    dialogBinding.etEmail.setAlpha(0.6f); // Visual indication that it's disabled
                }
            }
            
            // Add real-time password validation
            dialogBinding.etPassword.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    String password = s.toString();
                    if (!password.isEmpty()) {
                        PasswordValidator.ValidationResult result = PasswordValidator.validatePassword(password);
                        if (!result.isValid()) {
                            dialogBinding.etPassword.setError(result.getErrorMessage());
                        } else {
                            dialogBinding.etPassword.setError(null);
                        }
                    } else {
                        dialogBinding.etPassword.setError(null);
                    }
                }
            });
            
            // Set click listeners
            dialogBinding.btnSave.setOnClickListener(v -> {
                String name = dialogBinding.etName.getText().toString().trim();
                String password = dialogBinding.etPassword.getText().toString().trim();
                
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (currentUser != null) {
                    // Split name into first and last name
                    String[] nameParts = name.split(" ", 2);
                    currentUser.setFirstName(nameParts[0]);
                    if (nameParts.length > 1) {
                        currentUser.setLastName(nameParts[1]);
                    } else {
                        currentUser.setLastName("");
                    }
                      // Update password only if provided
                    if (!password.isEmpty()) {
                        // Validate password strength
                        PasswordValidator.ValidationResult passwordValidation = PasswordValidator.validatePasswordWithDetailedMessage(password);
                        if (!passwordValidation.isValid()) {
                            Toast.makeText(requireContext(), passwordValidation.getErrorMessage(), Toast.LENGTH_LONG).show();
                            return;
                        }
                        currentUser.setPassword(password);
                    }
                    
                    executor.execute(() -> {
                        try {
                            db.userDao().update(currentUser);
                              requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), getString(R.string.profile_updated_success), Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating user profile", e);                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), getString(R.string.profile_update_failed), Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                }
            });
            
            dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
            
            dialog.show();
              } catch (Exception e) {
            Log.e(TAG, "Error showing edit profile dialog", e);
            Toast.makeText(requireContext(), getString(R.string.error_opening_edit_dialog), Toast.LENGTH_SHORT).show();
        }
    }
      private void showSettingsDialog() {
        try {
            DialogSettingsBinding dialogBinding = DialogSettingsBinding.inflate(LayoutInflater.from(requireContext()));
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
            builder.setView(dialogBinding.getRoot());
            builder.setTitle("⚙️ Pengaturan");
            
            // Get current settings
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            boolean isDarkMode = prefs.getBoolean("DARK_MODE", false);
            
            // Set current value for dark mode switch
            dialogBinding.switchDarkMode.setChecked(isDarkMode);
            
            // Create dialog
            androidx.appcompat.app.AlertDialog dialog = builder.create();
            
            // Set listener for dark mode switch with animation
            dialogBinding.switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("DARK_MODE", isChecked).apply();
                
                // Add haptic feedback for better UX
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    buttonView.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                }
            });
            
            dialogBinding.btnSave.setOnClickListener(v -> {
                dialog.dismiss();
                
                // Apply theme if it was changed
                boolean newDarkMode = prefs.getBoolean("DARK_MODE", false);
                if (newDarkMode != isDarkMode) {
                    AppCompatDelegate.setDefaultNightMode(
                        newDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                    requireActivity().recreate();
                }
                
                Toast.makeText(requireContext(), "Pengaturan tersimpan", Toast.LENGTH_SHORT).show();
            });
            
            dialogBinding.btnCancel.setOnClickListener(v -> {
                // Revert dark mode changes
                prefs.edit().putBoolean("DARK_MODE", isDarkMode).apply();
                dialog.dismiss();
            });
            
            dialog.show();
              } catch (Exception e) {
            Log.e(TAG, "Error showing settings dialog", e);
            Toast.makeText(requireContext(), getString(R.string.error_opening_settings), Toast.LENGTH_SHORT).show();
        }
    }
      private void showLogoutConfirmation() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.logout_title))
                .setMessage(getString(R.string.logout_message))
                .setPositiveButton(getString(R.string.logout), (dialog, which) -> logout())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }
    
    private void logout() {
        try {
            // Clear user data from SharedPreferences
            SharedPreferences prefs = requireActivity().getSharedPreferences("USER_PREFS", 0);
            prefs.edit().clear().apply();
            
            SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            defaultPrefs.edit().remove("USER_ID").apply();
            
            Toast.makeText(requireContext(), getString(R.string.logged_out_success), Toast.LENGTH_SHORT).show();
            
            // Navigate to login screen
            try {
                NavController navController = Navigation.findNavController(requireView());
                navController.navigate(R.id.action_profileFragment_to_loginFragment);
                requireActivity().finish();
            } catch (Exception e) {
                Log.e(TAG, "Navigation error during logout", e);
                requireActivity().finish();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during logout", e);
            Toast.makeText(requireContext(), getString(R.string.logout_error), Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleTheme() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        boolean isDarkMode = prefs.getBoolean("DARK_MODE", false);
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            prefs.edit().putBoolean("DARK_MODE", false).apply();
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            prefs.edit().putBoolean("DARK_MODE", true).apply();
        }
        
        requireActivity().recreate();
    }    private void openCustomizeProfileActivity() {
        try {
            Log.d(TAG, "Opening CustomizeProfileActivity...");
            
            // Check if user is valid before opening activity
            if (currentUser == null) {
                Toast.makeText(requireContext(), "Please wait for profile to load", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Create intent to open CustomizeProfileActivity
            Intent intent = new Intent(requireContext(), com.example.glean.activity.CustomizeProfileActivity.class);
            
            // Pass user data if needed
            intent.putExtra("USER_ID", userId);
            
            // Start activity
            startActivity(intent);
            Log.d(TAG, "CustomizeProfileActivity opened successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening CustomizeProfileActivity", e);
            Toast.makeText(requireContext(), "Failed to open profile customization", Toast.LENGTH_SHORT).show();
        }    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showImageSourceDialog();
            } else {
                Toast.makeText(requireContext(), "Storage permission required to select image", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                captureImageFromCamera();
            } else {
                Toast.makeText(requireContext(), "Camera permission required to take photo", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh user data when returning to fragment
        if (userId != -1 && currentUser != null) {
            loadUserData();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    private void createDefaultUserIfNeeded() {
        executor.execute(() -> {
            try {
                // Check if any user exists first
                int userCount = db.userDao().getUserCount();                if (userCount == 0) {
                    // Create default user
                    UserEntity defaultUser = new UserEntity();
                    defaultUser.setUsername("User Baru");
                    defaultUser.setFirstName("User");
                    defaultUser.setLastName("Baru");
                    defaultUser.setEmail("user@glean.app");
                    defaultUser.setCreatedAt(System.currentTimeMillis());
                    defaultUser.setPoints(0);
                    
                    long newUserId = db.userDao().insert(defaultUser);
                    
                    // Update current userId
                    userId = (int) newUserId;
                    
                    // Save user ID to preferences
                    SharedPreferences prefs = requireActivity().getSharedPreferences("USER_PREFS", 0);
                    prefs.edit().putInt("USER_ID", userId).apply();
                    
                    SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                    defaultPrefs.edit().putInt("USER_ID", userId).apply();
                    
                    requireActivity().runOnUiThread(() -> {
                        Log.d(TAG, "Default user created with ID: " + newUserId);
                        Toast.makeText(requireContext(), "Profile created successfully!", Toast.LENGTH_SHORT).show();
                        // Reload user data
                        loadUserData();
                    });
                } else {
                    // Get first available user and update userId
                    UserEntity firstUser = db.userDao().getFirstUser();
                    if (firstUser != null) {
                        userId = firstUser.getId();
                        
                        // Save to preferences
                        SharedPreferences prefs = requireActivity().getSharedPreferences("USER_PREFS", 0);
                        prefs.edit().putInt("USER_ID", userId).apply();
                        
                        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                        defaultPrefs.edit().putInt("USER_ID", userId).apply();
                        
                        requireActivity().runOnUiThread(() -> {
                            Log.d(TAG, "Using existing user with ID: " + userId);
                            // Reload user data
                            loadUserData();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating default user", e);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Error creating profile", Toast.LENGTH_SHORT).show();
                    setDefaultUIValues();
                });            }        });
    }
      
    // Skin Selection Methods
    private void openSkinSelection() {
        try {
            Intent intent = new Intent(requireContext(), SkinSelectionActivity.class);
            startActivityForResult(intent, SKIN_SELECTION_REQUEST);
        } catch (Exception e) {
            Log.e(TAG, "Error opening skin selection", e);
            Toast.makeText(requireContext(), "Failed to open skin selection", Toast.LENGTH_SHORT).show();
        }
    }
      private void updateProfileSkin() {
        if (binding.profileSkinBackground != null) {
            SharedPreferences prefs = requireActivity().getSharedPreferences("profile_settings", 0);
            String currentSkin = prefs.getString("selected_skin", "default");
            
            int skinResource = getSkinResource(currentSkin);
            binding.profileSkinBackground.setBackgroundResource(skinResource);
            
            Log.d(TAG, "Profile skin updated to: " + currentSkin);
        }
    }
    
    private int getSkinResource(String skinId) {
        switch (skinId) {
            case "nature":
                return R.drawable.profile_skin_nature;
            case "ocean":
                return R.drawable.profile_skin_ocean;
            case "sunset":
                return R.drawable.profile_skin_sunset;
            case "galaxy":
                return R.drawable.profile_skin_galaxy;
            default:
                return R.drawable.profile_skin_default;
        }
    }
  
}