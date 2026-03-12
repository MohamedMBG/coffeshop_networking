package com.example.loyaltyapp.viewmodels;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.example.loyaltyapp.data.repository.ConfigRepository;
import com.example.loyaltyapp.data.repository.UserRepository;
import com.example.loyaltyapp.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class MainViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ConfigRepository mockConfigRepo;
    private UserRepository mockUserRepo;
    private FirebaseAuth mockAuth;
    private FirebaseUser mockUser;

    private MainViewModel viewModel;

    @Before
    public void setup() {
        mockConfigRepo = mock(ConfigRepository.class);
        mockUserRepo = mock(UserRepository.class);
        mockAuth = mock(FirebaseAuth.class);
        mockUser = mock(FirebaseUser.class);
    }

    @Test
    public void testInit_UserLoggedIn_ListensToBothRepos() {
        // Arrange
        when(mockAuth.getCurrentUser()).thenReturn(mockUser);
        when(mockUser.getUid()).thenReturn("user123");

        // Act
        viewModel = new MainViewModel(mockConfigRepo, mockUserRepo, mockAuth);

        // Assert
        verify(mockConfigRepo).listenToAppStatus(any(MutableLiveData.class));
        verify(mockUserRepo).listenToUser(eq("user123"), any(MutableLiveData.class));
    }

    @Test
    public void testInit_UserNotLoggedIn_ListensOnlyToConfigRepo() {
        // Arrange
        when(mockAuth.getCurrentUser()).thenReturn(null);

        // Act
        viewModel = new MainViewModel(mockConfigRepo, mockUserRepo, mockAuth);

        // Assert
        verify(mockConfigRepo).listenToAppStatus(any(MutableLiveData.class));
        // Verify user repo is never asked to listen to a user if not logged in
        verify(mockUserRepo, org.mockito.Mockito.never()).listenToUser(any(), any());
    }
}
