package com.example.loyaltyapp.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.loyaltyapp.data.repository.UserRepository;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class ProfileViewModel extends ViewModel {
    private final UserRepository userRepository;
    private final MutableLiveData<DocumentSnapshot> userData = new MutableLiveData<>();
    private final MutableLiveData<SaveState> saveState = new MutableLiveData<>();
    private String currentUid;

    public ProfileViewModel() {
        userRepository = new UserRepository();
    }

    public void init(String uid) {
        if (this.currentUid == null || !this.currentUid.equals(uid)) {
            this.currentUid = uid;
            userRepository.listenToUser(uid, userData);
        }
    }

    public LiveData<DocumentSnapshot> getUserData() {
        return userData;
    }

    public LiveData<SaveState> getSaveState() {
        return saveState;
    }

    public boolean isValidMoroccanPhone(String phone) {
        // Moroccan phone format: 10 digits starting with 05, 06, or 07
        if (phone == null)
            return false;
        return phone.matches("^(05|06|07)\\d{8}$");
    }

    public void saveProfile(String name, String birthday, String phone, String address, String gender) {
        if (name == null || name.isEmpty() || birthday == null || birthday.isEmpty() ||
                phone == null || phone.isEmpty() || address == null || address.isEmpty() || gender == null) {
            saveState.setValue(new SaveState(false, "Please fill in all required fields", false));
            return;
        }

        if (!isValidMoroccanPhone(phone)) {
            saveState.setValue(new SaveState(false, "Enter a valid Moroccan phone number (05xxxxxxxx)", false));
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

        saveState.setValue(new SaveState(true, "Saving...", false));

        userRepository.saveProfile(currentUid, update, new UserRepository.OnSaveCompleteListener() {
            @Override
            public void onSuccess() {
                saveState.setValue(new SaveState(false, "Profile details saved successfully!", true));
            }

            @Override
            public void onFailure(String error) {
                saveState.setValue(new SaveState(false, "Failed to save profile: " + error, false));
            }
        });
    }

    public void resetSaveState() {
        saveState.setValue(null);
    }

    @Override
    protected void onCleared() {
        userRepository.cleanup();
        super.onCleared();
    }

    public static class SaveState {
        public final boolean isLoading;
        public final String message;
        public final boolean isSuccess;

        public SaveState(boolean isLoading, String message, boolean isSuccess) {
            this.isLoading = isLoading;
            this.message = message;
            this.isSuccess = isSuccess;
        }
    }
}
