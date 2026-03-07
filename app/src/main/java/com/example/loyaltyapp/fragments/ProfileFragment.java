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
import androidx.lifecycle.ViewModelProvider;

import com.example.loyaltyapp.LoyaltyActivity;
import com.example.loyaltyapp.R;
import com.example.loyaltyapp.SignUpActivity;
import com.example.loyaltyapp.viewmodels.ProfileViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Calendar;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private com.example.loyaltyapp.databinding.FragmentProfileBinding binding;

    // UI (affichage uniquement)
    private MaterialToolbar toolbar;
    private TextView tvName, tvEmail, tvPhone, tvBirthday, tvPoints, tvGender;
    private MaterialCardView editCard;
    private TextInputEditText inputFullName, inputBirthday, inputPhone, inputAddress;
    private RadioButton radioMale, radioFemale;
    private MaterialButton btnSave;
    private RadioGroup genderGroup;

    // (facultatif) raccourcis
    private LinearLayout layoutNotifications, layoutSavedRewards, layoutHelp, layoutTerms, layoutPrivacy;
    MaterialCardView logoutLayout;

    private ProfileViewModel viewModel;
    private FirebaseAuth auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = com.example.loyaltyapp.databinding.FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // MVVM Setup
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        // Check Auth immediately
        auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(requireContext(), SignUpActivity.class));
            requireActivity().finish();
            return;
        }

        // Bind vues
        toolbar = binding.toolbar;
        tvName = binding.tvName;
        tvEmail = binding.tvEmail;
        tvPhone = binding.tvPhone;
        tvBirthday = binding.tvBirthday;
        tvPoints = binding.tvPoints;
        tvGender = binding.tvGender;

        editCard = binding.edit2;
        inputFullName = binding.inputFullName;
        inputBirthday = binding.inputBirthday;
        inputPhone = binding.inputPhone;
        inputAddress = binding.inputAddress;
        radioMale = binding.radioMale;
        radioFemale = binding.radioFemale;
        genderGroup = binding.genderGroup;
        btnSave = binding.btnSaveProfile;

        layoutNotifications = binding.layoutNotifications;
        layoutSavedRewards = binding.layoutSavedRewards;
        layoutHelp = binding.layoutHelp;
        layoutTerms = binding.layoutTerms;
        layoutPrivacy = binding.layoutPrivacy;
        logoutLayout = binding.layoutSignOut;

        // UI Basic Setup
        tvEmail.setText(user.getEmail() != null ? user.getEmail() : getString(R.string.profile_email_placeholder));
        String phone = user.getPhoneNumber();
        tvPhone.setText(!TextUtils.isEmpty(phone) ? phone : getString(R.string.profile_phone_placeholder));
        inputBirthday.setOnClickListener(x -> showDatePicker());
        btnSave.setOnClickListener(x -> collectAndSaveProfile());

        logoutLayout.setOnClickListener(v1 -> logOut());
        if (layoutNotifications != null)
            layoutNotifications.setOnClickListener(x -> showSnack("Notifications: bientôt"));
        if (layoutSavedRewards != null)
            layoutSavedRewards.setOnClickListener(x -> showSnack("Saved rewards: bientôt"));
        if (layoutHelp != null)
            layoutHelp.setOnClickListener(x -> showSnack("Help: bientôt"));
        if (layoutTerms != null)
            layoutTerms.setOnClickListener(x -> showSnack("Terms: bientôt"));
        if (layoutPrivacy != null)
            layoutPrivacy.setOnClickListener(x -> showSnack("Privacy: bientôt"));

        // Initialize fetching data
        viewModel.init(user.getUid());

        // Observer pattern for UI changes
        viewModel.getUserData().observe(getViewLifecycleOwner(), documentSnap -> {
            if (documentSnap == null) {
                Toast.makeText(requireContext(), "Failed to load profile.", Toast.LENGTH_SHORT).show();
            } else {
                bindUser(documentSnap);
            }
        });

        // Observer pattern for save status
        viewModel.getSaveState().observe(getViewLifecycleOwner(), saveState -> {
            if (saveState == null)
                return;

            if (saveState.isLoading) {
                btnSave.setEnabled(false);
                btnSave.setText(saveState.message);
            } else {
                btnSave.setEnabled(true);
                btnSave.setText(R.string.profile_details_save);
                Toast.makeText(requireContext(), saveState.message, Toast.LENGTH_SHORT).show();

                if (saveState.isSuccess && getActivity() instanceof LoyaltyActivity) {
                    ((LoyaltyActivity) getActivity()).onProfileCompleted();
                }
                viewModel.resetSaveState();
            }
        });
    }

    private void showSnack(String message) {
        View anchor = requireView();
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT)
                .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                .show();
    }

    private void bindUser(@NonNull DocumentSnapshot doc) {
        String fullName = doc.getString("fullName");
        String birthday = doc.getString("birthday");
        String gender = doc.getString("gender");
        Long points = doc.getLong("points");
        Boolean verified = doc.getBoolean("isVerified");
        String phone = doc.getString("phone");
        String address = doc.getString("address");

        tvName.setText(!TextUtils.isEmpty(fullName) ? fullName : getString(R.string.profile_name_placeholder));
        tvBirthday.setText(!TextUtils.isEmpty(birthday)
                ? getString(R.string.profile_birthday_value, birthday)
                : getString(R.string.profile_birthday_placeholder));
        tvGender.setText(!TextUtils.isEmpty(gender)
                ? getString(R.string.profile_gender_value, gender)
                : getString(R.string.profile_gender_placeholder));
        tvPoints.setText(points != null ? String.valueOf(points) : "0");
        tvPhone.setText(!TextUtils.isEmpty(phone) ? phone : getString(R.string.profile_phone_placeholder));

        boolean isVerified = verified != null ? verified : false;
        editCard.setVisibility(isVerified ? View.GONE : View.VISIBLE);

        if (!isVerified) {
            if (inputFullName != null && TextUtils.isEmpty(inputFullName.getText()))
                inputFullName.setText(fullName != null ? fullName : "");
            if (inputBirthday != null && TextUtils.isEmpty(inputBirthday.getText()))
                inputBirthday.setText(birthday != null ? birthday : "");
            if (inputPhone != null && TextUtils.isEmpty(inputPhone.getText()))
                inputPhone.setText(phone != null ? phone : "");
            if (inputAddress != null && TextUtils.isEmpty(inputAddress.getText()))
                inputAddress.setText(address != null ? address : "");

            if (!TextUtils.isEmpty(gender)) {
                if ("male".equalsIgnoreCase(gender))
                    genderGroup.check(radioMale.getId());
                else if ("female".equalsIgnoreCase(gender))
                    genderGroup.check(radioFemale.getId());
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
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void collectAndSaveProfile() {
        if (inputFullName == null || inputBirthday == null)
            return;

        String name = inputFullName.getText() != null ? inputFullName.getText().toString().trim() : "";
        String birthday = inputBirthday.getText() != null ? inputBirthday.getText().toString().trim() : "";
        String phone = inputPhone.getText() != null ? inputPhone.getText().toString().trim() : "";
        String address = inputAddress.getText() != null ? inputAddress.getText().toString().trim() : "";

        int checkedId = genderGroup.getCheckedRadioButtonId();
        String gender = null;
        if (checkedId == radioMale.getId())
            gender = "male";
        else if (checkedId == radioFemale.getId())
            gender = "female";

        // Send logic completely to View Model
        viewModel.saveProfile(name, birthday, phone, address, gender);
    }

    private void logOut() {
        if (auth != null)
            auth.signOut();
        startActivity(new Intent(requireContext(), SignUpActivity.class));
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
