package com.example.loyaltyapp.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.loyaltyapp.data.repository.ConfigRepository;
import com.example.loyaltyapp.data.repository.MenuRepository;
import com.example.loyaltyapp.models.MenuItemModel;

import java.util.List;
import java.util.Map;

public class HomeViewModel extends ViewModel {

    private final MenuRepository menuRepo;
    private final ConfigRepository configRepo;

    // Use LiveData to notify the Fragment
    private final MutableLiveData<List<MenuItemModel>> menuList = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Object>> bannerConfig = new MutableLiveData<>();

    public HomeViewModel() {
        // Initialize Respositories
        menuRepo = new MenuRepository();
        configRepo = new ConfigRepository();

        // Load default lists
        loadPopularItems();
        configRepo.listenToBannerConfig(bannerConfig);
    }

    public LiveData<List<MenuItemModel>> getMenuData() {
        return menuList;
    }

    public LiveData<Map<String, Object>> getBannerConfigData() {
        return bannerConfig;
    }

    // Interaction triggers
    public void loadCategory(String category) {
        if (category == null) {
            loadPopularItems();
        } else {
            menuRepo.listenToCategory(category, menuList);
        }
    }

    private void loadPopularItems() {
        menuRepo.listenToPopularItems(menuList);
    }

    @Override
    protected void onCleared() {
        // Ensure no memory leaks with Firestore connections
        menuRepo.cleanup();
        configRepo.cleanup();
        super.onCleared();
    }
}
