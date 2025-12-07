package com.example.loyaltyapp.fragments;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.loyaltyapp.R;
import com.example.loyaltyapp.SignUpActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileFragment extends Fragment {

    // UI (affichage uniquement)
    private MaterialToolbar toolbar;
    private TextView tvName, tvEmail, tvPhone, tvBirthday, tvPoints, tvGender,tvAddress;
    private MaterialCardView editCard;
    private TextInputEditText inputFullName, inputBirthday;
    private RadioButton radioMale, radioFemale;
    private MaterialButton btnSave;
    private RadioGroup genderGroup;
    private TextInputEditText inputPhone, inputAddress;  // Add this line

    // (facultatif) raccourcis
    private LinearLayout layoutNotifications, layoutSavedRewards, layoutHelp, layoutTerms, layoutPrivacy;
    MaterialCardView logoutLayout;

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String uid;
    private ListenerRegistration userListener;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
        // Remplace R.layout.fragment_profile par le nom exact de ton fichier si différent
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // Init Firebase
        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(requireContext(), SignUpActivity.class));
            requireActivity().finish();
            return;
        }
        uid = user.getUid();

        // Bind vues (affichage)
        toolbar     = v.findViewById(R.id.toolbar);
        tvName      = v.findViewById(R.id.tvName);
        tvEmail     = v.findViewById(R.id.tvEmail);
        tvPhone     = v.findViewById(R.id.tvPhone);
        tvBirthday  = v.findViewById(R.id.tvBirthday);
        tvPoints    = v.findViewById(R.id.tvPoints);
        tvGender    = v.findViewById(R.id.tvGender);

        editCard       = v.findViewById(R.id.edit2);
        inputFullName  = v.findViewById(R.id.inputFullName);
        inputBirthday  = v.findViewById(R.id.inputBirthday);
        radioMale      = v.findViewById(R.id.radioMale);
        radioFemale    = v.findViewById(R.id.radioFemale);
        genderGroup    = v.findViewById(R.id.genderGroup);
        btnSave        = v.findViewById(R.id.btnSaveProfile);

        // (facultatif) autres layouts si présents
        layoutNotifications = v.findViewById(R.id.layoutNotifications);
        layoutSavedRewards  = v.findViewById(R.id.layoutSavedRewards);
        layoutHelp          = v.findViewById(R.id.layoutHelp);
        layoutTerms         = v.findViewById(R.id.layoutTerms);
        layoutPrivacy       = v.findViewById(R.id.layoutPrivacy);
        logoutLayout        = v.findViewById(R.id.layoutSignOut);
        inputPhone = v.findViewById(R.id.inputPhone);
        inputAddress = v.findViewById(R.id.inputAddress);

        //log out process


        // Renseigner email / phone depuis FirebaseAuth
        tvEmail.setText(user.getEmail() != null ? user.getEmail() : getString(R.string.profile_email_placeholder));
        String phone = user.getPhoneNumber();
        tvPhone.setText(!TextUtils.isEmpty(phone) ? phone : getString(R.string.profile_phone_placeholder));
        inputBirthday.setOnClickListener(x -> showDatePicker());
        btnSave.setOnClickListener(x -> saveProfile());

        // Écoute temps réel du document utilisateur (affichage only)
        DocumentReference userRef = db.collection("users").document(uid);
        userListener = userRef.addSnapshotListener((snapshot, e) -> {
            if (!isAdded()) return;
            if (e != null) {
                Toast.makeText(requireContext(), "Failed to load profile.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (snapshot != null && snapshot.exists()) bindUser(snapshot);
        });

        // Sign out
        logoutLayout.setOnClickListener(v1 -> {
            logOut();
        });
        // Listeners (replace your Toasts with showSnack(...))
        if (layoutNotifications != null) layoutNotifications.setOnClickListener(x -> showSnack("Notifications: bientôt"));
        if (layoutSavedRewards != null) layoutSavedRewards.setOnClickListener(x -> showSnack("Saved rewards: bientôt"));
        if (layoutHelp != null) layoutHelp.setOnClickListener(x -> showSnack("Help: bientôt"));
        if (layoutTerms != null) layoutTerms.setOnClickListener(x -> showSnack("Terms: bientôt"));
        if (layoutPrivacy != null) layoutPrivacy.setOnClickListener(x -> showSnack("Privacy: bientôt"));
    }

    // Helper (put inside your Fragment)
    private void showSnack(String message) {
        // anchor to the Fragment root view (works even without CoordinatorLayout)
        View anchor = requireView();
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT)
                .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                .setAction("OK", v -> {}) // optional action
                .show();
    }
    private void bindUser(@NonNull DocumentSnapshot doc) {
        String fullName = doc.getString("fullName");
        String birthday = doc.getString("birthday");
        String gender   = doc.getString("gender");
        Long points     = doc.getLong("points");
        Boolean verified = doc.getBoolean("isVerified");
        String phone = doc.getString("phone");  // Add this
        String address = doc.getString("address");  // Add this

        tvName.setText(!TextUtils.isEmpty(fullName) ? fullName : getString(R.string.profile_name_placeholder));
        tvBirthday.setText(!TextUtils.isEmpty(birthday)
                ? getString(R.string.profile_birthday_value, birthday)
                : getString(R.string.profile_birthday_placeholder));
        tvGender.setText(!TextUtils.isEmpty(gender)
                ? getString(R.string.profile_gender_value, gender)
                : getString(R.string.profile_gender_placeholder));
        tvPoints.setText(points != null ? String.valueOf(points) : "0");

        // Add phone and address display
        tvPhone.setText(!TextUtils.isEmpty(phone)
                ? phone
                : getString(R.string.profile_phone_placeholder));
        tvAddress.setText(!TextUtils.isEmpty(address)
                ? address
                : getString(R.string.profile_address_placeholder));

        boolean isVerified = verified != null ? verified : false;
        editCard.setVisibility(isVerified ? View.GONE : View.VISIBLE);
        if (!isVerified) {
            if (inputFullName != null) inputFullName.setText(fullName != null ? fullName : "");
            if (inputBirthday != null) inputBirthday.setText(birthday != null ? birthday : "");

            // Set phone and address in edit form
            if (inputPhone != null) inputPhone.setText(phone != null ? phone : "");
            if (inputAddress != null) inputAddress.setText(address != null ? address : "");

            if (!TextUtils.isEmpty(gender)) {
                if ("male".equalsIgnoreCase(gender)) genderGroup.check(radioMale.getId());
                else if ("female".equalsIgnoreCase(gender)) genderGroup.check(radioFemale.getId());
            } else {
                genderGroup.clearCheck();
            }
        }
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(requireContext(),
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    String formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    inputBirthday.setText(formatted);
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void saveProfile() {
        if (inputFullName == null || inputBirthday == null) return;

        String name = inputFullName.getText() != null ? inputFullName.getText().toString().trim() : "";
        String birthday = inputBirthday.getText() != null ? inputBirthday.getText().toString().trim() : "";
        String phone = inputPhone.getText() != null ? inputPhone.getText().toString().trim() : "";
        String address = inputAddress.getText() != null ? inputAddress.getText().toString().trim() : "";

        int checkedId = genderGroup.getCheckedRadioButtonId();
        String gender = null;
        if (checkedId == radioMale.getId()) gender = "male";
        else if (checkedId == radioFemale.getId()) gender = "female";

        if (name.isEmpty()) { inputFullName.setError(getString(R.string.profile_edit_name_error)); return; }
        if (birthday.isEmpty()) { inputBirthday.setError(getString(R.string.profile_edit_birthday_error)); return; }
        if (phone.isEmpty()) {
            inputPhone.setError(getString(R.string.profile_edit_phone_error));
            return;
        }
        // Optional: Add phone format validation for Morocco
        if (!isValidMoroccanPhone(phone)) {
            inputPhone.setError("Enter a valid Moroccan phone number (05xxxxxxxx)");
            return;
        }
        if (gender == null) {
            Toast.makeText(requireContext(), R.string.profile_gender_error, Toast.LENGTH_SHORT).show();
            return;
        }

        String formattedPhone = "+212 " + phone;

        Map<String, Object> update = new HashMap<>();
        update.put("fullName", name);
        update.put("birthday", birthday);
        update.put("gender", gender);
        update.put("phone", formattedPhone);
        update.put("address", address);
        update.put("isVerified", true);
        update.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .set(update, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(requireContext(), R.string.profile_details_saved, Toast.LENGTH_SHORT).show();
                    if (getActivity() instanceof com.example.loyaltyapp.LoyaltyActivity) {
                        ((com.example.loyaltyapp.LoyaltyActivity) getActivity()).onProfileCompleted();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Failed to save profile: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // 5. Add this helper method for phone validation
    private boolean isValidMoroccanPhone(String phone) {
        // Moroccan phone format: 10 digits starting with 05, 06, or 07
        return phone.matches("^(05|06|07)\\d{8}$");
    }

    private void logOut() {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(requireContext(), SignUpActivity.class));
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }
}
