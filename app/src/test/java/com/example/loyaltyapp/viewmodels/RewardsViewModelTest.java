package com.example.loyaltyapp.viewmodels;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.example.loyaltyapp.data.repository.RewardsRepository;
import com.example.loyaltyapp.data.repository.UserRepository;
import com.example.loyaltyapp.models.User;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RewardsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private RewardsRepository mockRewardsRepo;
    private UserRepository mockUserRepo;

    private RewardsViewModel viewModel;

    @Before
    public void setup() {
        mockRewardsRepo = mock(RewardsRepository.class);
        mockUserRepo = mock(UserRepository.class);
        // Do not instantiate viewModel here since its constructor triggers loadRewards()
    }

    @Test
    public void testInit_LoadsRewardsAndListensToUser() {
        // Act
        viewModel = new RewardsViewModel(mockRewardsRepo, mockUserRepo);
        viewModel.init("user123");

        // Assert
        // Verified it attempts to fetch rewards using the default "all" filter
        verify(mockRewardsRepo).fetchRewards(eq("all"), any(RewardsRepository.OnRewardsLoaded.class));
        
        // Verified it started listening to the user via UserRepository
        verify(mockUserRepo).listenToUser(eq("user123"), any(MutableLiveData.class));
    }

    @Test
    public void testSetFilter_ReloadsWithNewFilter() {
        // Arrange
        viewModel = new RewardsViewModel(mockRewardsRepo, mockUserRepo);

        // Act
        viewModel.setFilter("Food");

        // Assert
        // Ensure that the repository was called with the specific filter
        verify(mockRewardsRepo).fetchRewards(eq("Food"), any(RewardsRepository.OnRewardsLoaded.class));
    }
}
