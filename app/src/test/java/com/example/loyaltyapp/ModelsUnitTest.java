package com.example.loyaltyapp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.example.loyaltyapp.models.MenuItemModel;
import com.example.loyaltyapp.models.Rewards;
import com.example.loyaltyapp.models.User;
import com.example.loyaltyapp.models.ActivityEvent;
import com.example.loyaltyapp.models.AppSettings;
import com.example.loyaltyapp.models.QRCode;
import com.example.loyaltyapp.models.Scans;

/**
 * Unit tests for the data models used in the loyalty app to ensure data
 * integrity and
 * correct behavior of getters, setters, and constructors.
 */
public class ModelsUnitTest {

    /**
     * Test the properties of the User model to make sure all getters and setters
     * work correctly.
     */
    @Test
    public void testUserModel() {
        // Create a user instance using the parameterized constructor
        User user = new User("uid123", "test@test.com", "John Doe", "01/01/2000", "Male", 100, 5, true);

        // Assert that the constructor correctly set the values
        assertEquals("uid123", user.getUid());
        assertEquals("test@test.com", user.getEmail());
        assertEquals("John Doe", user.getFullName());
        assertEquals("01/01/2000", user.getBirthday());
        assertEquals("Male", user.getGender());
        assertEquals(100, user.getPoints());
        assertEquals(5, user.getVisits());
        assertTrue(user.isVerified());

        // Test setters
        user.setPoints(150);
        assertEquals(150, user.getPoints());

        user.setVerified(false);
        assertFalse(user.isVerified());
    }

    /**
     * Test the properties of the Rewards model to ensure properly structured reward
     * objects.
     */
    @Test
    public void testRewardsModel() {
        // Create a Reward using the parameterized constructor
        Rewards reward = new Rewards("r_1", "Free Coffee", 25.0, 50, "path/to/image", "Beverages", true);

        // Check if properties match the ones provided in the constructor
        assertEquals("r_1", reward.id);
        assertEquals("Free Coffee", reward.name);
        assertEquals(25.0, reward.priceMAD, 0.01);
        assertEquals(50, reward.redeemPoints);
        assertEquals("path/to/image", reward.imagePath);
        assertEquals("Beverages", reward.category);
        assertTrue(reward.active);

        // Test modifying a public property directly
        reward.active = false;
        assertFalse(reward.active);
    }

    /**
     * Test the MenuItemModel behavior, verifying it accurately holds menu item
     * data.
     */
    @Test
    public void testMenuItemModel() {
        // Instantiate the menu item model
        MenuItemModel menuItem = new MenuItemModel();

        // Set properties
        menuItem.setId("item1");
        menuItem.setName("Burger");
        menuItem.setPriceMAD(45.5);
        menuItem.setCategory("Food");
        menuItem.setImageUrl("url");
        menuItem.setIsAvailable(true);
        menuItem.setIsPopular(false);
        menuItem.setPopularityScore(100L);

        // Verify getters return the correct values
        assertEquals("item1", menuItem.getId());
        assertEquals("Burger", menuItem.getName());
        assertEquals(45.5, menuItem.getPriceMAD(), 0.01);
        assertEquals("Food", menuItem.getCategory());
        assertEquals("url", menuItem.getImageUrl());
        assertTrue(menuItem.getIsAvailable());
        assertFalse(menuItem.getIsPopular());
        assertEquals(Long.valueOf(100), menuItem.getPopularityScore());
    }

    /**
     * Test empty/placeholder models to ensure they can be instantiated without
     * crashing.
     */
    @Test
    public void testEmptyModels() {
        AppSettings appSettings = new AppSettings();
        assertNotNull(appSettings);

        QRCode qrCode = new QRCode();
        assertNotNull(qrCode);

        Scans scans = new Scans();
        assertNotNull(scans);

        ActivityEvent activityEvent = new ActivityEvent();
        assertNotNull(activityEvent);
    }

    /**
     * Test the API models for requests and responses.
     */
    @Test
    public void testApiModels() {
        // Email request
        EmailRequest emailRequest = new EmailRequest("test@test.com", "uid123");
        assertNotNull(emailRequest); // Verification relies on successful instantiation since fields are private

        // Email response
        EmailResponse emailResponse = new EmailResponse();
        assertFalse(emailResponse.isOk());
        assertNull(emailResponse.getError());

        // Verify request
        VerifyRequest verifyRequest = new VerifyRequest("token123", "uid123");
        assertNotNull(verifyRequest);

        // Verify response
        VerifyResponse verifyResponse = new VerifyResponse();
        assertFalse(verifyResponse.isOk());
        assertNull(verifyResponse.getError());
    }
}
